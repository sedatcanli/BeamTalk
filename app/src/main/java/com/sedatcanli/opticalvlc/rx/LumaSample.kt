package com.sedatcanli.opticalvlc.rx

/** A single brightness observation of the tracked light blob. */
data class LumaSample(
    val timeNs: Long,
    val luma: Float
)
