package com.sedatcanli.opticalvlc.core

/**
 * Bit/byte helpers shared by the transmitter and the receiver.
 *
 * Every multi-bit value in the optical link is transmitted most-significant
 * bit first. Manchester chips are represented as Int 1/0 where 1 means the
 * LED/torch is ON.
 */
object BitUtils {

    fun bytesToBits(bytes: ByteArray): IntArray {
        val bits = IntArray(bytes.size * 8)
        for (i in bytes.indices) {
            val value = bytes[i].toInt() and 0xFF
            for (b in 0 until 8) {
                bits[i * 8 + b] = (value ushr (7 - b)) and 1
            }
        }
        return bits
    }

    fun bitsToBytes(bits: IntArray): ByteArray {
        require(bits.size % 8 == 0) { "Bit count must be a multiple of 8" }
        val bytes = ByteArray(bits.size / 8)
        for (i in bits.indices) {
            if (bits[i] and 1 == 1) {
                bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (7 - (i % 8)))).toByte()
            }
        }
        return bytes
    }

    fun byteToBits(value: Int): IntArray {
        val bits = IntArray(8)
        for (b in 0 until 8) {
            bits[b] = (value ushr (7 - b)) and 1
        }
        return bits
    }

    fun bitsToInt(bits: IntArray, offset: Int, count: Int): Int {
        var value = 0
        for (i in 0 until count) {
            value = (value shl 1) or (bits[offset + i] and 1)
        }
        return value
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString(" ") {
        "%02X".format(it.toInt() and 0xFF)
    }
}
