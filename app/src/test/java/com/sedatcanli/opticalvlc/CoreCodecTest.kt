package com.sedatcanli.opticalvlc

import com.sedatcanli.opticalvlc.core.BitUtils
import com.sedatcanli.opticalvlc.core.Crc8
import com.sedatcanli.opticalvlc.core.FrameCodec
import com.sedatcanli.opticalvlc.core.Language
import com.sedatcanli.opticalvlc.core.Manchester
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCodecTest {

    @Test
    fun crc8KnownVector() {
        val value = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0xF4, Crc8.compute(value))
    }

    @Test
    fun bitOrderIsMsbFirst() {
        assertArrayEquals(
            intArrayOf(1, 0, 1, 0, 1, 0, 1, 0),
            BitUtils.byteToBits(0xAA)
        )
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0x01),
            BitUtils.bitsToBytes(
                intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1)
            )
        )
    }

    @Test
    fun manchesterEncodingMatchesSpec() {
        // Bit 0 -> on,off ; bit 1 -> off,on
        assertArrayEquals(intArrayOf(1, 0, 0, 1), Manchester.encode(intArrayOf(0, 1)))
        assertArrayEquals(intArrayOf(0, 1), Manchester.encode(intArrayOf(1)))
        assertEquals(0, Manchester.decodePair(1, 0))
        assertEquals(1, Manchester.decodePair(0, 1))
        assertNull(Manchester.decodePair(1, 1))
        assertNull(Manchester.decodePair(0, 0))
    }

    @Test
    fun frameRoundTrip() {
        val bytes = FrameCodec.encode("Merhaba dünya", Language.TURKISH)
        assertEquals(FrameCodec.PREAMBLE, bytes[0].toInt() and 0xFF)
        assertEquals(Language.TURKISH.id, bytes[1].toInt() and 0xFF)
        assertEquals(bytes.size - 4, bytes[2].toInt() and 0xFF)

        val parsed = FrameCodec.parse(bytes)
        assertNotNull(parsed)
        assertEquals("Merhaba dünya", parsed!!.payload)
        assertEquals(Language.TURKISH, parsed.language)
        assertEquals(
            Crc8.compute(bytes, 1, bytes.size - 2),
            bytes.last().toInt() and 0xFF
        )
    }

    @Test
    fun frameRoundTripMaxPayload() {
        val payload = "a".repeat(FrameCodec.MAX_PAYLOAD_BYTES)
        val parsed = FrameCodec.parse(FrameCodec.encode(payload, Language.ENGLISH))
        assertNotNull(parsed)
        assertEquals(FrameCodec.MAX_PAYLOAD_BYTES, parsed!!.payloadBytes.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun payloadOverLimitIsRejected() {
        FrameCodec.encode("a".repeat(FrameCodec.MAX_PAYLOAD_BYTES + 1), Language.ENGLISH)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPayloadIsRejected() {
        FrameCodec.encode("   ", Language.ENGLISH)
    }

    @Test
    fun corruptedPayloadFailsCrc() {
        val bytes = FrameCodec.encode("hello world", Language.ENGLISH)
        bytes[3] = (bytes[3].toInt() xor 0x01).toByte()
        assertNull(FrameCodec.parse(bytes))
    }

    @Test
    fun badPreambleIsRejected() {
        val bytes = FrameCodec.encode("hello", Language.ENGLISH)
        bytes[0] = 0x55
        assertNull(FrameCodec.parse(bytes))
    }

    @Test
    fun invalidUtf8IsRejected() {
        val payload = byteArrayOf(0xC3.toByte(), 0x28)
        val frame = FrameCodec.encodeBytes(payload, Language.ENGLISH.id)
        assertNull(FrameCodec.parse(frame))
    }

    @Test
    fun utf8MultiBytePayloadKeepsLengthInBytes() {
        val payload = "Türkçe ışık"
        val bytes = FrameCodec.encode(payload, Language.TURKISH)
        val length = bytes[2].toInt() and 0xFF
        assertEquals(payload.toByteArray(Charsets.UTF_8).size, length)
        assertEquals(payload, FrameCodec.parse(bytes)?.payload)
    }

    @Test
    fun payloadCanContainPreambleByte() {
        // U+00EA encodes as C3 AA, so the second UTF-8 byte collides with 0xAA.
        val payload = "\u00EA"
        val bytes = FrameCodec.encode(payload, Language.ENGLISH)
        assertTrue((bytes[4].toInt() and 0xFF) == 0xAA)
        assertEquals(payload, FrameCodec.parse(bytes)?.payload)
    }
}
