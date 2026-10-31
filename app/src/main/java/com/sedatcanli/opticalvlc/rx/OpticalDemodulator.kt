package com.sedatcanli.opticalvlc.rx

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Online burst segmenter + finite state machine.
 *
 * Camera frames arrive at ~30 fps. The demodulator keeps an adaptive window
 * of recent luma values, decides when the transmitter's light is "on", cuts
 * the stream into bursts (a burst = one complete frame) and hands each burst
 * to [BurstDecoder].
 *
 * States:
 *  * [RxPhase.IDLE]                  – waiting for light above the threshold
 *  * [RxPhase.PREAMBLE_ACQUISITION]  – burst detected, collecting chips
 *  * [RxPhase.READ_HEADER_AND_BODY]  – burst is long enough to contain a frame
 *  * [RxPhase.CRC_CHECK]             – burst finished, decoding
 */
class OpticalDemodulator(
    private val decoder: BurstDecoder = BurstDecoder()
) {

    enum class RxPhase {
        IDLE,
        PREAMBLE_ACQUISITION,
        READ_HEADER_AND_BODY,
        CRC_CHECK,
        DECODED,
        ERROR
    }

    data class State(
        val phase: RxPhase = RxPhase.IDLE,
        val luma: Float = 0f,
        val threshold: Float = 0f,
        val contrast: Float = 0f,
        val burstActive: Boolean = false,
        val burstDurationMs: Long = 0L
    )

    sealed interface Event {
        data class Decoded(
            val frame: com.sedatcanli.opticalvlc.core.FrameCodec.ParsedFrame,
            val bitRateHz: Double,
            val durationMs: Long,
            val contrast: Double
        ) : Event

        data class Rejected(val reason: String, val durationMs: Long) : Event
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val window = ArrayDeque<LumaSample>(WINDOW_MAX_SAMPLES)
    private val burst = ArrayList<LumaSample>(512)
    private val preRoll = ArrayDeque<LumaSample>(PRE_ROLL)

    private var burstActive = false
    private var burstStartNs = 0L
    private var lastActiveNs = 0L
    private var lastStatePatchNs = 0L

    @Synchronized
    fun onSample(sample: LumaSample) {
        if (sample.timeNs <= 0L) return

        window.addLast(sample)
        while (window.size > WINDOW_MAX_SAMPLES ||
            sample.timeNs - window.first().timeNs > WINDOW_DURATION_NS
        ) {
            window.removeFirst()
        }

        val stats = windowStats(window)
        val edge = sample.luma - stats.low
        val active = stats.contrast >= MIN_CONTRAST &&
            edge >= MIN_EDGE_DELTA &&
            edge >= stats.contrast * ACTIVE_FRACTION
        val inactive = edge <= stats.contrast * INACTIVE_FRACTION

        if (!burstActive) {
            if (active) {
                burstActive = true
                burst.clear()
                burst.addAll(preRoll)
                preRoll.clear()
                burstStartNs = sample.timeNs
                lastActiveNs = sample.timeNs
                burst.add(sample)
                publish(sample, stats, RxPhase.PREAMBLE_ACQUISITION)
            } else {
                preRoll.addLast(sample)
                while (preRoll.size > PRE_ROLL) preRoll.removeFirst()
                publish(sample, stats, RxPhase.IDLE)
            }
            return
        }

        burst.add(sample)
        if (!inactive) {
            lastActiveNs = sample.timeNs
        }

        val elapsed = sample.timeNs - burstStartNs
        val phase = when {
            elapsed > PREAMBLE_HINT_NS -> RxPhase.READ_HEADER_AND_BODY
            else -> RxPhase.PREAMBLE_ACQUISITION
        }

        if (sample.timeNs - lastActiveNs > BURST_END_TIMEOUT_NS) {
            finishBurst(stats)
        } else {
            publish(sample, stats, phase)
        }
    }

    @Synchronized
    fun reset() {
        window.clear()
        burst.clear()
        preRoll.clear()
        burstActive = false
        burstStartNs = 0L
        lastActiveNs = 0L
        lastStatePatchNs = 0L
        _state.value = State()
    }

    private fun finishBurst(stats: WindowStats) {
        val durationMs = (lastActiveNs - burstStartNs) / 1_000_000L
        val samples = burst.toList()
        burst.clear()
        burstActive = false
        preRoll.clear()

        _state.value = State(
            phase = RxPhase.CRC_CHECK,
            luma = samples.lastOrNull()?.luma ?: 0f,
            threshold = stats.threshold.toFloat(),
            contrast = stats.contrast.toFloat(),
            burstActive = false,
            burstDurationMs = durationMs
        )

        when (val outcome = decoder.decode(samples)) {
            is BurstDecoder.Outcome.Decoded -> {
                val value = outcome.value
                val bitRate = if (value.bitPeriodNs > 0) 1_000_000_000.0 / value.bitPeriodNs else 0.0
                _state.value = State(
                    phase = RxPhase.DECODED,
                    luma = samples.lastOrNull()?.luma ?: 0f,
                    threshold = stats.threshold.toFloat(),
                    contrast = value.contrast.toFloat(),
                    burstActive = false,
                    burstDurationMs = durationMs
                )
                _events.tryEmit(
                    Event.Decoded(
                        frame = value.frame,
                        bitRateHz = bitRate,
                        durationMs = durationMs,
                        contrast = value.contrast
                    )
                )
            }

            is BurstDecoder.Outcome.Failed -> {
                _state.value = State(
                    phase = RxPhase.ERROR,
                    luma = samples.lastOrNull()?.luma ?: 0f,
                    threshold = stats.threshold.toFloat(),
                    contrast = stats.contrast.toFloat(),
                    burstActive = false,
                    burstDurationMs = durationMs
                )
                _events.tryEmit(Event.Rejected(outcome.reason, durationMs))
            }
        }
    }

    private fun publish(sample: LumaSample, stats: WindowStats, phase: RxPhase) {
        // Avoid flooding StateFlow 30 times per second.
        if (sample.timeNs - lastStatePatchNs < STATE_MIN_INTERVAL_NS &&
            _state.value.phase == phase
        ) {
            _state.value = _state.value.copy(
                luma = sample.luma,
                threshold = stats.threshold.toFloat(),
                contrast = stats.contrast.toFloat(),
                burstActive = burstActive,
                burstDurationMs = if (burstActive) {
                    (sample.timeNs - burstStartNs) / 1_000_000L
                } else {
                    _state.value.burstDurationMs
                }
            )
            return
        }
        lastStatePatchNs = sample.timeNs
        _state.value = State(
            phase = phase,
            luma = sample.luma,
            threshold = stats.threshold.toFloat(),
            contrast = stats.contrast.toFloat(),
            burstActive = burstActive,
            burstDurationMs = if (burstActive) {
                (sample.timeNs - burstStartNs) / 1_000_000L
            } else {
                _state.value.burstDurationMs
            }
        )
    }

    private data class WindowStats(val low: Double, val threshold: Double, val contrast: Double)

    private fun windowStats(window: ArrayDeque<LumaSample>): WindowStats {
        if (window.isEmpty()) return WindowStats(0.0, 127.0, 0.0)
        val sorted = window.map { it.luma.toDouble() }.sorted()
        val low = sorted[((sorted.size - 1) * 0.05).toInt()]
        val high = sorted[((sorted.size - 1) * 0.95).toInt()]
        val contrast = high - low
        return WindowStats(low = low, threshold = (low + high) / 2.0, contrast = contrast)
    }

    companion object {
        private const val WINDOW_DURATION_NS = 2_000_000_000L
        private const val WINDOW_MAX_SAMPLES = 120
        private const val PRE_ROLL = 3
        private const val MIN_CONTRAST = 10.0
        private const val MIN_EDGE_DELTA = 25.0
        private const val ACTIVE_FRACTION = 0.45
        private const val INACTIVE_FRACTION = 0.30
        private const val BURST_END_TIMEOUT_NS = 800_000_000L
        private const val PREAMBLE_HINT_NS = 1_500_000_000L
        private const val STATE_MIN_INTERVAL_NS = 40_000_000L
    }
}
