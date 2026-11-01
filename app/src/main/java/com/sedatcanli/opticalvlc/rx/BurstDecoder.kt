package com.sedatcanli.opticalvlc.rx

import com.sedatcanli.opticalvlc.core.BitUtils
import com.sedatcanli.opticalvlc.core.FrameCodec

/**
 * Offline (batch) Manchester demodulator.
 *
 * The receiver collects all luma samples of one optical burst and decodes it
 * here. Working on the full burst means we can:
 *
 *  * derive the threshold from the burst's own dark/bright percentiles,
 *  * lock onto the Manchester mid-bit edges,
 *  * follow those edges bit by bit so camera timestamp jitter and dropped
 *    frames do not accumulate into clock drift, and
 *  * try every possible 0xAA alignment instead of relying on a fragile
 *    online state machine.
 *
 * The decoder is platform independent and unit-tested with synthetic camera
 * traces.
 */
class BurstDecoder(
    private val minBitRateHz: Double = 2.0,
    private val maxBitRateHz: Double = 30.0,
    private val minContrast: Double = 10.0
) {

    data class Success(
        val frame: FrameCodec.ParsedFrame,
        val bitPeriodNs: Double,
        val chipPeriodNs: Double,
        val contrast: Double
    )

    sealed interface Outcome {
        data class Decoded(val value: Success) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    private data class Transition(val timeNs: Long, val levelAfter: Int)

    fun decode(samples: List<LumaSample>): Outcome {
        if (samples.size < 8) return Outcome.Failed("Yetersiz Ã¶rnek")

        val sorted = samples.map { it.luma.toDouble() }.sorted()
        val low = percentile(sorted, 0.05)
        val high = percentile(sorted, 0.95)
        val contrast = high - low
        if (contrast < minContrast) {
            return Outcome.Failed("Kontrast yetersiz (${contrast.toInt()})")
        }

        val threshold = (low + high) / 2.0
        val band = maxOf(2.0, contrast * 0.12)
        val levels = IntArray(samples.size)
        var current = if (samples[0].luma >= threshold + band) 1 else 0
        levels[0] = current
        for (i in 1 until samples.size) {
            val value = samples[i].luma
            if (current == 0 && value >= threshold + band) {
                current = 1
            } else if (current == 1 && value <= threshold - band) {
                current = 0
            }
            levels[i] = current
        }

        val rawTransitions = ArrayList<Transition>(32)
        for (i in 1 until samples.size) {
            if (levels[i] != levels[i - 1]) {
                rawTransitions.add(
                    Transition(
                        timeNs = crossingTime(samples[i - 1], samples[i], threshold),
                        levelAfter = levels[i]
                    )
                )
            }
        }

        // Drop contact-bounce transitions that are faster than one camera frame.
        val transitions = ArrayList<Transition>(rawTransitions.size)
        for (transition in rawTransitions) {
            if (transitions.isNotEmpty() &&
                transition.timeNs - transitions.last().timeNs < MIN_TRANSITION_GAP_NS
            ) {
                continue
            }
            transitions.add(transition)
        }

        if (transitions.size < 4) {
            return Outcome.Failed("Preamble kenarÄ± bulunamadÄ±")
        }

        val minBitPeriod = 1_000_000_000.0 / maxBitRateHz
        val maxBitPeriod = 1_000_000_000.0 / minBitRateHz
        var crcFailure = false
        var attempts = 0

        for (i in 0..transitions.size - 4) {
            if (attempts >= MAX_ANCHOR_ATTEMPTS) break

            val d0 = transitions[i + 1].timeNs - transitions[i].timeNs
            val d1 = transitions[i + 2].timeNs - transitions[i + 1].timeNs
            val d2 = transitions[i + 3].timeNs - transitions[i + 2].timeNs

            if (d0 < minBitPeriod * 0.7 || d0 > maxBitPeriod * 1.3) continue
            if (d1 < minBitPeriod * 0.7 || d1 > maxBitPeriod * 1.3) continue
            if (d2 < minBitPeriod * 0.7 || d2 > maxBitPeriod * 1.3) continue

            val average = (d0 + d1 + d2) / 3.0
            if (average < minBitPeriod || average > maxBitPeriod) continue
            val tolerance = average * 0.35
            if (d0 < average - tolerance || d0 > average + tolerance) continue
            if (d1 < average - tolerance || d1 > average + tolerance) continue
            if (d2 < average - tolerance || d2 > average + tolerance) continue

            // A preamble produces alternating edge directions.
            if (transitions[i].levelAfter == transitions[i + 1].levelAfter) continue
            if (transitions[i + 1].levelAfter == transitions[i + 2].levelAfter) continue
            if (transitions[i + 2].levelAfter == transitions[i + 3].levelAfter) continue

            attempts++
            val chipPeriod = average / 2.0
            val outcome = decodeAt(
                transitions = transitions,
                anchorIndex = i,
                chipPeriodNs = chipPeriod,
                firstNs = samples.first().timeNs,
                lastNs = samples.last().timeNs,
                contrast = contrast
            )
            if (outcome is Outcome.Decoded) return outcome
            if (outcome is Outcome.Failed && outcome.reason.startsWith("CRC")) {
                crcFailure = true
            }
        }

        return if (crcFailure) {
            Outcome.Failed("CRC hatasÄ± (bozuk paket)")
        } else {
            Outcome.Failed("Preamble bulunamadÄ±")
        }
    }

    private fun decodeAt(
        transitions: List<Transition>,
        anchorIndex: Int,
        chipPeriodNs: Double,
        firstNs: Long,
        lastNs: Long,
        contrast: Double
    ): Outcome {
        val bitPeriodNs = chipPeriodNs * 2.0
        val anchor = transitions[anchorIndex]

        // Manchester always carries one mid-bit edge per bit. Instead of sampling
        // a fixed clock (which drifts with quantised frame times), follow those
        // edges: every expected edge is snapped to the nearest transition within
        // a fraction of a bit period, and the bit comes straight from the edge
        // direction. Boundary edges sit half a bit away and are ignored.
        val forwardBits = ArrayList<Int>(MAX_DECODED_BITS)
        forwardBits.add(anchor.levelAfter)
        var period = bitPeriodNs
        var previous = anchor.timeNs
        var steps = 0
        while (previous < lastNs + period && forwardBits.size < MAX_DECODED_BITS) {
            val expected = (previous + period).toLong()
            val snapped = nearestTransition(transitions, expected, period * SNAP_WINDOW)
            if (snapped != null) {
                forwardBits.add(snapped.levelAfter)
                period = smoothPeriod(period, (snapped.timeNs - previous).toDouble())
                previous = snapped.timeNs
            } else {
                forwardBits.add(-1)
                previous = expected
            }
            if (++steps > MAX_DECODED_BITS) break
        }

        val backwardBits = ArrayList<Int>(64)
        period = bitPeriodNs
        previous = anchor.timeNs
        steps = 0
        while (previous > firstNs - period && backwardBits.size < MAX_DECODED_BITS) {
            val expected = (previous - period).toLong()
            val snapped = nearestTransition(transitions, expected, period * SNAP_WINDOW)
            if (snapped != null) {
                backwardBits.add(snapped.levelAfter)
                period = smoothPeriod(period, (previous - snapped.timeNs).toDouble())
                previous = snapped.timeNs
            } else {
                backwardBits.add(-1)
                previous = expected
            }
            if (++steps > MAX_DECODED_BITS) break
        }

        val bits = IntArray(backwardBits.size + forwardBits.size) { -1 }
        for (index in backwardBits.indices) {
            bits[backwardBits.size - 1 - index] = backwardBits[index]
        }
        for (index in forwardBits.indices) {
            bits[backwardBits.size + index] = forwardBits[index]
        }

        var sawCrcCandidate = false
        for (j in 0..bits.size - 8) {
            if (bits[j] != 1) continue
            if (!isPreambleAt(bits, j)) continue
            // The byte after idle is the only valid preamble; the idle gap
            // decodes to an illegal Manchester pair (-1).
            if (j > 0 && bits[j - 1] >= 0) continue

            val length = BitUtils.bitsToInt(bits, j + 16, 8)
            if (length !in FrameCodec.MIN_PAYLOAD_BYTES..FrameCodec.MAX_PAYLOAD_BYTES) continue

            val frameBits = 24 + length * 8 + 8
            if (j + frameBits > bits.size) continue

            var valid = true
            for (b in j until j + frameBits) {
                if (bits[b] < 0) {
                    valid = false
                    break
                }
            }
            if (!valid) continue

            val bytes = BitUtils.bitsToBytes(bits.copyOfRange(j, j + frameBits))
            if (bytes[0].toInt() and 0xFF != FrameCodec.PREAMBLE) continue

            val parsed = FrameCodec.parse(bytes)
            if (parsed != null) {
                return Outcome.Decoded(
                    Success(
                        frame = parsed,
                        bitPeriodNs = bitPeriodNs,
                        chipPeriodNs = chipPeriodNs,
                        contrast = contrast
                    )
                )
            }
            sawCrcCandidate = true
        }

        return if (sawCrcCandidate) {
            Outcome.Failed("CRC hatasÄ± (bozuk paket)")
        } else {
            Outcome.Failed("Preamble bulunamadÄ±")
        }
    }

    private fun isPreambleAt(bits: IntArray, offset: Int): Boolean {
        if (offset + 8 > bits.size) return false
        for (i in 0 until 8) {
            val expected = if (i % 2 == 0) 1 else 0
            if (bits[offset + i] != expected) return false
        }
        return true
    }

    private fun nearestTransition(
        transitions: List<Transition>,
        targetNs: Long,
        windowNs: Double
    ): Transition? {
        if (transitions.isEmpty()) return null
        var low = 0
        var high = transitions.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (transitions[mid].timeNs <= targetNs) low = mid + 1 else high = mid - 1
        }
        val before = transitions.getOrNull(low - 1)
        val after = transitions.getOrNull(low)
        val candidate = when {
            before == null -> after
            after == null -> before
            targetNs - before.timeNs <= after.timeNs - targetNs -> before
            else -> after
        } ?: return null
        return if (kotlin.math.abs(candidate.timeNs - targetNs) <= windowNs) candidate else null
    }

    private fun smoothPeriod(current: Double, observed: Double): Double {
        if (observed < current * 0.6 || observed > current * 1.4) return current
        return current * 0.85 + observed * 0.15
    }

    private fun crossingTime(a: LumaSample, b: LumaSample, threshold: Double): Long {
        val span = (b.timeNs - a.timeNs).toDouble()
        val delta = (b.luma - a.luma).toDouble()
        if (span <= 0.0 || kotlin.math.abs(delta) < 1e-6) {
            return (a.timeNs + b.timeNs) / 2
        }
        val fraction = ((threshold - a.luma) / delta).coerceIn(0.0, 1.0)
        return a.timeNs + (span * fraction).toLong()
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    companion object {
        private const val MIN_TRANSITION_GAP_NS = 12_000_000L
        private const val MAX_ANCHOR_ATTEMPTS = 24
        private const val MAX_DECODED_BITS = 1400
        private const val SNAP_WINDOW = 0.32
    }
}


