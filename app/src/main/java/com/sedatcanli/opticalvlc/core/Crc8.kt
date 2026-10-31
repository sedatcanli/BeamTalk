package com.sedatcanli.opticalvlc.core

/**
 * CRC-8 with polynomial 0x07, initial value 0x00 and no reflection/xor-out
 * (the "CRC-8/SMBUS" variant). This is the cheapest checksum that still
 * catches every single-bit error and every burst up to 8 bits.
 */
object Crc8 {

    private const val POLY = 0x07

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0x00
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) {
                    ((crc shl 1) xor POLY) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc and 0xFF
    }

    fun check(data: ByteArray, expected: Int): Boolean = compute(data) == (expected and 0xFF)
}
