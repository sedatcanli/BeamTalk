package com.sedatcanli.opticalvlc.core

/**
 * Manchester line code.
 *
 *  * bit `0` -> HIGH then LOW (torch on, torch off)
 *  * bit `1` -> LOW then HIGH (torch off, torch on)
 *
 * Every bit therefore contains exactly one mid-bit edge. That edge is what
 * the receiver locks onto, which makes the link tolerant to clock drift as
 * long as the preamble is present.
 */
object Manchester {

    /** Expands [bits] into chips (2 chips per bit). 1 = light on, 0 = off. */
    fun encode(bits: IntArray): IntArray {
        val chips = IntArray(bits.size * 2)
        for (i in bits.indices) {
            if (bits[i] and 1 == 0) {
                chips[i * 2] = 1
                chips[i * 2 + 1] = 0
            } else {
                chips[i * 2] = 0
                chips[i * 2 + 1] = 1
            }
        }
        return chips
    }

    /**
     * Decodes one chip pair. Returns null when the pair is not a legal
     * Manchester symbol (for example both chips dark during idle).
     */
    fun decodePair(firstChip: Int, secondChip: Int): Int? = when {
        firstChip == 1 && secondChip == 0 -> 0
        firstChip == 0 && secondChip == 1 -> 1
        else -> null
    }

    /**
     * Decodes a chip stream into bits. Invalid pairs are reported as -1 so
     * callers can detect idle gaps and noise instead of silently inventing
     * data.
     */
    fun decode(chips: IntArray): IntArray {
        require(chips.size % 2 == 0) { "Chip count must be even" }
        return IntArray(chips.size / 2) { i ->
            decodePair(chips[i * 2], chips[i * 2 + 1]) ?: -1
        }
    }
}
