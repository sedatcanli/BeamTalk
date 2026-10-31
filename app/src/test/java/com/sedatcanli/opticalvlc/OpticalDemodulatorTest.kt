package com.sedatcanli.opticalvlc

import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.rx.OpticalDemodulator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalDemodulatorTest {

    @Test
    fun decodesBurstEndToEnd() = runBlocking {
        val frame = FrameCodec.encode("Merhaba", Language.TURKISH)
        val samples = TestSignals.synthesize(frame, bitRateHz = 4.0, jitterMs = 4.0)
        val demodulator = OpticalDemodulator()

        val collector = async { demodulator.events.first() }
        yield()
        samples.forEach(demodulator::onSample)

        val event = withTimeout(2_000) { collector.await() }
        assertTrue("Olay $event", event is OpticalDemodulator.Event.Decoded)
        assertEquals(
            "Merhaba",
            (event as OpticalDemodulator.Event.Decoded).frame.payload
        )
    }

    @Test
    fun reportsRejectedPackage() = runBlocking {
        val frame = FrameCodec.encode("bozuk", Language.TURKISH)
        frame[4] = (frame[4].toInt() xor 0x02).toByte()
        val samples = TestSignals.synthesize(frame, bitRateHz = 3.0)
        val demodulator = OpticalDemodulator()

        val collector = async { demodulator.events.first() }
        yield()
        samples.forEach(demodulator::onSample)

        val event = withTimeout(2_000) { collector.await() }
        assertTrue("Olay $event", event is OpticalDemodulator.Event.Rejected)
    }

    @Test
    fun resetClearsBurst() {
        val frame = FrameCodec.encode("reset testi", Language.TURKISH)
        val samples = TestSignals.synthesize(frame, bitRateHz = 2.0)
        val demodulator = OpticalDemodulator()

        samples.take(samples.size / 2).forEach(demodulator::onSample)
        demodulator.reset()
        TestSignals.darkIdle().forEach(demodulator::onSample)

        assertEquals(OpticalDemodulator.RxPhase.IDLE, demodulator.state.value.phase)
        assertTrue(demodulator.state.value.luma < 60f)
    }
}
