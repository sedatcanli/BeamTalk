package com.sedatcanli.opticalvlc.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    if (ms < 1000L) return "${ms} ms"
    val totalSeconds = ms / 1000.0
    return if (totalSeconds < 60.0) {
        String.format(Locale.US, "%.1f sn", totalSeconds)
    } else {
        val minutes = (totalSeconds / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        String.format(Locale.US, "%d dk %02d sn", minutes, seconds)
    }
}

fun formatClock(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
