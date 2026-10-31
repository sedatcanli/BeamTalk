package com.sedatcanli.opticalvlc.tx

import com.sedatcanli.opticalvlc.core.BitUtils
import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.core.Manchester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Drives the LED torch with hard real-time-ish chip timing.
 *
 * All waits are absolute deadlines computed from a single `System.nanoTime()`
 * reference, so the 33 ms jitter of the UI thread and GC pauses cannot
 * accumulate into clock drift. The last millisecond before every edge is
 * busy-spun to avoid `parkNanos` oversleep.
 */
class TorchTransmitter(private val torch: TorchController) {

    data class State(
        val transmitting: Boolean = false,
        val progress: Float = 0f,
        val totalBits: Int = 0,
        val totalChips: Int = 0,
        val frameBytes: Int = 0,
        val estimatedDurationMs: Long = 0L,
        val repeatIndex: Int = 0,
        val repeats: Int = 1,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val cancelRequested = AtomicBoolean(false)

    @Volatile
    private var worker: Thread? = null

    fun transmit(
        text: String,
        language: Language,
        bitRateHz: Int,
        repeats: Int
    ): Boolean {
        if (worker?.isAlive == true) return false
        if (!torch.isAvailable) {
            _state.value = State(error = "Bu cihazda flaş bulunamadı")
            return false
        }

        val frame = try {
            FrameCodec.encode(text, language)
        } catch (e: IllegalArgumentException) {
            _state.value = State(error = e.message ?: "Geçersiz mesaj")
            return false
        }

        val bits = BitUtils.bytesToBits(frame)
        val chips = Manchester.encode(bits)
        val rate = bitRateHz.coerceIn(MIN_BIT_RATE_HZ, MAX_BIT_RATE_HZ)
        val chipPeriodNs = 1_000_000_000.0 / (2.0 * rate)
        val repeatCount = repeats.coerceIn(1, MAX_REPEATS)
        val repLengthNs = chips.size * chipPeriodNs + REPEAT_GAP_NS
        val totalNs = LEAD_IN_NS + repeatCount * repLengthNs

        cancelRequested.set(false)
        _state.value = State(
            transmitting = true,
            progress = 0f,
            totalBits = bits.size,
            totalChips = chips.size * repeatCount,
            frameBytes = frame.size,
            estimatedDurationMs = (totalNs / 1_000_000L).toLong(),
            repeatIndex = 0,
            repeats = repeatCount
        )

        val thread = Thread({
            runTransmission(frame, chips, chipPeriodNs, repeatCount, repLengthNs)
        }, "vlc-torch-tx").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
        }
        worker = thread
        thread.start()
        return true
    }

    private fun runTransmission(
        frame: ByteArray,
        chips: IntArray,
        chipPeriodNs: Double,
        repeats: Int,
        repLengthNs: Double
    ) {
        val startNs = System.nanoTime()
        val totalNs = LEAD_IN_NS + repeats * repLengthNs
        var lastUpdateNs = 0L
        var completed = false

        try {
            torch.setTorch(false)
            if (!parkUntil((startNs + LEAD_IN_NS).toLong())) return

            outer@ for (repeat in 0 until repeats) {
                val repetitionStart = startNs + LEAD_IN_NS + repeat * repLengthNs
                for (index in chips.indices) {
                    val deadline = repetitionStart + index * chipPeriodNs
                    if (!parkUntil(deadline.toLong())) break@outer
                    torch.setTorch(chips[index] == 1)

                    val now = System.nanoTime()
                    if (now - lastUpdateNs >= PROGRESS_INTERVAL_NS) {
                        lastUpdateNs = now
                        val progress = ((now - startNs).toDouble() / totalNs)
                            .coerceIn(0.0, 1.0)
                        _state.value = _state.value.copy(
                            progress = progress.toFloat(),
                            repeatIndex = repeat + 1
                        )
                    }
                }

                // Turn off between repetitions at a fixed instant.
                if (!parkUntil((repetitionStart + chips.size * chipPeriodNs).toLong())) {
                    break
                }
                torch.setTorch(false)
                if (!parkUntil((repetitionStart + repLengthNs).toLong())) {
                    break
                }
                completed = true
            }
        } finally {
            torch.setTorch(false)
            val cancelled = cancelRequested.get()
            _state.value = if (cancelled || !completed) {
                _state.value.copy(
                    transmitting = false,
                    progress = if (cancelled) _state.value.progress else 0f,
                    error = if (cancelled) null else _state.value.error
                )
            } else {
                _state.value.copy(
                    transmitting = false,
                    progress = 1f,
                    repeatIndex = repeats
                )
            }
            worker = null
        }
    }

    /**
     * Waits until [deadlineNs]. Returns false if the transmission was cancelled.
     */
    private fun parkUntil(deadlineNs: Long): Boolean {
        while (true) {
            if (cancelRequested.get()) return false
            val remaining = deadlineNs - System.nanoTime()
            if (remaining <= 0L) return true
            if (remaining > SPIN_THRESHOLD_NS) {
                LockSupport.parkNanos(remaining - SPIN_THRESHOLD_NS / 2)
            } else {
                while (System.nanoTime() < deadlineNs) {
                    if (cancelRequested.get()) return false
                }
                return true
            }
        }
    }

    fun cancel() {
        cancelRequested.set(true)
        worker?.interrupt()
    }

    fun release() {
        cancel()
        torch.forceOff()
        worker?.join(500)
        worker = null
    }

    companion object {
        const val MIN_BIT_RATE_HZ = 2
        const val MAX_BIT_RATE_HZ = 10
        const val MAX_REPEATS = 5

        private const val LEAD_IN_NS = 500_000_000.0
        private const val REPEAT_GAP_NS = 500_000_000.0
        private const val SPIN_THRESHOLD_NS = 1_000_000L
        private const val PROGRESS_INTERVAL_NS = 80_000_000L

        fun estimateDurationMs(payloadBytes: Int, bitRateHz: Int, repeats: Int): Long {
            val bits = (FrameCodec.HEADER_SIZE + payloadBytes + FrameCodec.TRAILER_SIZE) * 8
            val safeRate = bitRateHz.coerceIn(MIN_BIT_RATE_HZ, MAX_BIT_RATE_HZ)
            val chipNs = 1_000_000_000.0 / (2.0 * safeRate)
            val repNs = bits * 2.0 * chipNs + REPEAT_GAP_NS
            val total = LEAD_IN_NS + repeats.coerceIn(1, MAX_REPEATS) * repNs
            return (total / 1_000_000L).toLong()
        }
    }
}
