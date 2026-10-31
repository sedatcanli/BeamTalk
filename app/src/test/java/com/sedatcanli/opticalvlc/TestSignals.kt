package com.sedatcanli.opticalvlc

import com.sedatcanli.opticalvlc.core.BitUtils
import com.sedatcanli.opticalvlc.core.Manchester
import com.sedatcanli.opticalvlc.rx.LumaSample
import java.util.Random

/**
 * Builds a synthetic 30 fps camera trace for a frame, including idle
 * lead-in/tail, optional timestamp jitter, amplitude noise and dropped
 * frames. Used by the demodulator unit tests.
 */
object TestSignals {

    fun synthesize(
        frame: ByteArray,
        bitRateHz: Double,
        fps: Double = 30.0,
        leadInMs: Long = 500,
        tailMs: Long = 1_000,
        noise: Double = 8.0,
        jitterMs: Double = 0.0,
        dropEvery: Int = 0,
        seed: Long = 7L,
        baseTimeNs: Long = 5_000_000_000L
    ): List<LumaSample> {
        val chips = Manchester.encode(BitUtils.bytesToBits(frame))
        val random = Random(seed)
        val chipPeriodNs = 1_000_000_000.0 / (2.0 * bitRateHz)
        val frameStart = baseTimeNs + leadInMs * 1_000_000L
        val frameEnd = frameStart + (chips.size * chipPeriodNs).toLong()
        val end = frameEnd + tailMs * 1_000_000L
        val intervalNs = (1_000_000_000.0 / fps).toLong()

        val samples = ArrayList<LumaSample>()
        var clock = baseTimeNs
        var index = 0
        while (clock < end) {
            val dropped = dropEvery > 0 && index % dropEvery == 0
            if (!dropped) {
                val jitter = if (jitterMs > 0.0) {
                    ((random.nextDouble() * 2.0 - 1.0) * jitterMs * 1_000_000.0).toLong()
                } else {
                    0L
                }
                val time = clock + jitter
                val level = if (time in frameStart until frameEnd) {
                    val chipIndex = ((time - frameStart) / chipPeriodNs)
                        .toInt()
                        .coerceIn(0, chips.lastIndex)
                    chips[chipIndex]
                } else {
                    0
                }
                val base = if (level == 1) 225.0 else 22.0
                val luma = (base + (random.nextDouble() * 2.0 - 1.0) * noise)
                    .coerceIn(0.0, 255.0)
                samples.add(LumaSample(time, luma.toFloat()))
            }
            index++
            clock += intervalNs
        }
        return samples
    }

    fun darkIdle(
        durationMs: Long = 2_000,
        fps: Double = 30.0,
        seed: Long = 3L,
        baseTimeNs: Long = 5_000_000_000L
    ): List<LumaSample> {
        val random = Random(seed)
        val intervalNs = (1_000_000_000.0 / fps).toLong()
        val samples = ArrayList<LumaSample>()
        var clock = baseTimeNs
        val end = baseTimeNs + durationMs * 1_000_000L
        while (clock < end) {
            val luma = (22.0 + (random.nextDouble() * 2.0 - 1.0) * 6.0)
                .coerceIn(0.0, 255.0)
            samples.add(LumaSample(clock, luma.toFloat()))
            clock += intervalNs
        }
        return samples
    }
}
