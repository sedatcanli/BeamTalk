package com.sedatcanli.opticalvlc.core

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Data-link frame:
 *
 * ```
 * [PREAMBLE 0xAA][LANG_ID 1B][LENGTH 1B][PAYLOAD N bytes][CRC-8 1B]
 * ```
 *
 * The CRC covers LANG_ID, LENGTH and PAYLOAD. All bytes are transmitted
 * MSB-first and then Manchester encoded by the physical layer.
 */
object FrameCodec {

    const val PREAMBLE: Int = 0xAA
    const val HEADER_SIZE: Int = 3 // preamble + lang + length
    const val TRAILER_SIZE: Int = 1 // crc
    const val MAX_PAYLOAD_BYTES: Int = 128
    const val MIN_PAYLOAD_BYTES: Int = 1

    data class ParsedFrame(
        val languageId: Int,
        val language: Language?,
        val payloadBytes: ByteArray,
        val payload: String,
        val crc: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ParsedFrame) return false
            return languageId == other.languageId &&
                payloadBytes.contentEquals(other.payloadBytes) &&
                crc == other.crc
        }

        override fun hashCode(): Int {
            var result = languageId
            result = 31 * result + payloadBytes.contentHashCode()
            result = 31 * result + crc
            return result
        }
    }

    fun encode(payload: String, language: Language): ByteArray {
        val bytes = payload.trim().toByteArray(StandardCharsets.UTF_8)
        return encodeBytes(bytes, language.id)
    }

    fun encodeBytes(payload: ByteArray, languageId: Int): ByteArray {
        require(payload.size in MIN_PAYLOAD_BYTES..MAX_PAYLOAD_BYTES) {
            "Payload must be ${MIN_PAYLOAD_BYTES}..${MAX_PAYLOAD_BYTES} UTF-8 bytes (was ${payload.size})"
        }
        require(languageId in 1..255) { "LANG_ID must fit in one byte" }

        val frame = ByteArray(HEADER_SIZE + payload.size + TRAILER_SIZE)
        frame[0] = PREAMBLE.toByte()
        frame[1] = languageId.toByte()
        frame[2] = payload.size.toByte()
        payload.copyInto(frame, destinationOffset = HEADER_SIZE)
        frame[frame.lastIndex] = Crc8.compute(
            frame,
            offset = 1,
            length = 2 + payload.size
        ).toByte()
        return frame
    }

    fun encodeBits(payload: String, language: Language): IntArray =
        BitUtils.bytesToBits(encode(payload, language))

    /**
     * Parses a complete frame byte array (preamble included). Returns null on
     * any structural or checksum problem.
     */
    fun parse(frame: ByteArray): ParsedFrame? {
        if (frame.size < HEADER_SIZE + MIN_PAYLOAD_BYTES + TRAILER_SIZE) return null
        if (frame[0].toInt() and 0xFF != PREAMBLE) return null

        val languageId = frame[1].toInt() and 0xFF
        val length = frame[2].toInt() and 0xFF
        if (length !in MIN_PAYLOAD_BYTES..MAX_PAYLOAD_BYTES) return null
        if (frame.size != HEADER_SIZE + length + TRAILER_SIZE) return null

        val expected = frame[frame.lastIndex].toInt() and 0xFF
        val actual = Crc8.compute(frame, offset = 1, length = 2 + length)
        if (expected != actual) return null

        val payloadBytes = frame.copyOfRange(HEADER_SIZE, HEADER_SIZE + length)
        val payload = decodeUtf8Strict(payloadBytes) ?: return null
        return ParsedFrame(
            languageId = languageId,
            language = Language.fromId(languageId),
            payloadBytes = payloadBytes,
            payload = payload,
            crc = expected
        )
    }

    /**
     * Decode [count] bytes starting at [offset] of a raw bit stream. The
     * stream must not contain invalid Manchester bits; callers guarantee that.
     */
    fun parseBits(bits: IntArray, offset: Int, bitCount: Int): ParsedFrame? {
        if (bitCount % 8 != 0 || bitCount < (HEADER_SIZE + MIN_PAYLOAD_BYTES + TRAILER_SIZE) * 8) {
            return null
        }
        if (offset < 0 || offset + bitCount > bits.size) return null
        val bytes = BitUtils.bitsToBytes(bits.copyOfRange(offset, offset + bitCount))
        return parse(bytes)
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: Exception) {
        null
    }
}
