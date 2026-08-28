package com.routine.domain

object RelativeTimeFormatter {
    private const val HOUR_MILLIS = 3_600_000L
    private const val DAY_MILLIS = 24 * HOUR_MILLIS

    fun format(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
        if (timestamp == null) return "Never"

        val elapsed = (now - timestamp).coerceAtLeast(0L)
        return when {
            elapsed < HOUR_MILLIS -> "Just now"
            elapsed < DAY_MILLIS -> plural(elapsed / HOUR_MILLIS, "hour") + " ago"
            else -> plural(elapsed / DAY_MILLIS, "day") + " ago"
        }
    }

    private fun plural(value: Long, unit: String): String =
        "$value $unit${if (value == 1L) "" else "s"}"
}

