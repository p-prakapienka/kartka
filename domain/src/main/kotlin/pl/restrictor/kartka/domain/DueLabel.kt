package pl.restrictor.kartka.domain

import kotlin.math.roundToInt

sealed interface DueText {
    data object Now : DueText
    data class Minutes(val minutes: Int) : DueText
    data class Hours(val hours: Int) : DueText
    data object Tomorrow : DueText
    data class Days(val days: Int) : DueText
}

object DueLabel {
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    fun of(dueAtEpochMs: Long, nowEpochMs: Long): DueText {
        val delta = dueAtEpochMs - nowEpochMs
        if (delta <= 0L) return DueText.Now
        if (delta < 60 * MINUTE_MS) {
            val minutes = ((delta + MINUTE_MS - 1) / MINUTE_MS).toInt().coerceAtLeast(1)
            return DueText.Minutes(minutes)
        }
        if (delta < 18 * HOUR_MS) {
            val hours = ((delta + HOUR_MS - 1) / HOUR_MS).toInt().coerceAtLeast(1)
            return DueText.Hours(hours)
        }
        val daysExact = delta.toDouble() / DAY_MS.toDouble()
        if (daysExact < 1.5) return DueText.Tomorrow
        return DueText.Days(daysExact.roundToInt().coerceAtLeast(2))
    }
}
