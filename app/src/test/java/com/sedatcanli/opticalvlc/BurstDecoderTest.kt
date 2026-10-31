package com.sedatcanli.opticalvlc

import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.rx.BurstDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstDecoderTest {

    private val decoder = BurstDecoder()

    @Test
    fun decodesSlowFrame() {
        val bytes = FrameCodec.encode("Merhaba dünya", Language.TURKISH)
        val samples = TestSignals.synthesize(bytes, bitRateHz = 2.0)
        val outcome = decoder.decode(samples)

        assertTrue("Beklenmeyen sonuç: $outcome", outcome is BurstDecoder.Outcome.Decoded)
        val frame = (outcome as BurstDecoder.Outcome.Decoded).value.frame
        assertEquals("Merhaba dünya", frame.payload)
        assertEquals(Language.TURKISH, frame.language)
        assertEquals(2.0, 1_000_000_000.0 / (outcome.value.bitPeriodNs), 0.1)
    }

    @Test
    fun decodesWithJitterAndNoise() {
        val bytes = FrameCodec.encode("hello optical world", Language.ENGLISH)
        val samples = TestSignals.synthesize(
            frame = bytes,
            bitRateHz = 4.0,
            jitterMs = 6.0,
            noise = 14.0,
            seed = 11L
        )
        val outcome = decoder.decode(samples)

        assertTrue("Beklenmeyen sonuç: $outcome", outcome is BurstDecoder.Outcome.Decoded)
        assertEquals(
            "hello optical world",
            (outcome as BurstDecoder.Outcome.Decoded).value.frame.payload
        )
    }

    @Test
    fun decodesWithDroppedFrames() {
        val bytes = FrameCodec.encode("düşen kareler", Language.TURKISH)
        val samples = TestSignals.synthesize(
            frame = bytes,
            bitRateHz = 5.0,
            dropEvery = 11,
            noise = 10.0,
            seed = 5L
        )
        val outcome = decoder.decode(samples)

        assertTrue("Beklenmeyen sonuç: $outcome", outcome is BurstDecoder.Outcome.Decoded)
        assertEquals(
            "düşen kareler",
            (outcome as BurstDecoder.Outcome.Decoded).value.frame.payload
        )
    }

    @Test
    fun decodesLongPayload() {
        val payload = "Optik iletisim " + "x".repeat(49)
        val bytes = FrameCodec.encode(payload, Language.ENGLISH)
        val samples = TestSignals.synthesize(
            frame = bytes,
            bitRateHz = 5.0,
            jitterMs = 3.0,
            noise = 8.0,
            seed = 23L
        )
        val outcome = decoder.decode(samples)

        assertTrue("Beklenmeyen sonuç: $outcome", outcome is BurstDecoder.Outcome.Decoded)
        assertEquals(
            payload,
            (outcome as BurstDecoder.Outcome.Decoded).value.frame.payload
        )
    }

    @Test
    fun rejectsCorruptedCrc() {
        val bytes = FrameCodec.encode("bozuk paket", Language.TURKISH)
        bytes[4] = (bytes[4].toInt() xor 0x04).toByte()
        val samples = TestSignals.synthesize(bytes, bitRateHz = 3.0)
        val outcome = decoder.decode(samples)

        assertTrue("CRC yakalanmalıydı: $outcome", outcome is BurstDecoder.Outcome.Failed)
        assertTrue(
            "Hata CRC olmalı: $outcome",
            (outcome as BurstDecoder.Outcome.Failed).reason.startsWith("CRC")
        )
    }

    @Test
    fun rejectsDarkIdle() {
        val outcome = decoder.decode(TestSignals.darkIdle())
        assertTrue(outcome is BurstDecoder.Outcome.Failed)
    }

    @Test
    fun rejectsTinyBurst() {
        val outcome = decoder.decode(TestSignals.darkIdle(durationMs = 100))
        assertTrue(outcome is BurstDecoder.Outcome.Failed)
    }
}
