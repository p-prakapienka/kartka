package pl.restrictor.kartka.domain

enum class Rating {
    BAD,
    MEDIUM,
    GOOD,
}

enum class DelayUnit {
    MINUTES,
    HOURS,
    DAYS,
}

data class DelayAmount(val count: Int, val unit: DelayUnit)

data class RepeatDelays(
    val badMs: Long,
    val mediumMs: Long,
    val goodMs: Long,
) {
    fun forRating(rating: Rating): Long = when (rating) {
        Rating.BAD -> badMs
        Rating.MEDIUM -> mediumMs
        Rating.GOOD -> goodMs
    }

    fun sanitized(): RepeatDelays = RepeatDelays(
        badMs = badMs.coerceIn(DelayAmounts.MIN_MS, DelayAmounts.MAX_MS),
        mediumMs = mediumMs.coerceIn(DelayAmounts.MIN_MS, DelayAmounts.MAX_MS),
        goodMs = goodMs.coerceIn(DelayAmounts.MIN_MS, DelayAmounts.MAX_MS),
    )

    companion object {
        val Default = RepeatDelays(
            badMs = DelayAmounts.toMillis(1, DelayUnit.HOURS),
            mediumMs = DelayAmounts.toMillis(1, DelayUnit.DAYS),
            goodMs = DelayAmounts.toMillis(7, DelayUnit.DAYS),
        )
    }
}

object DelayAmounts {
    const val MIN_MS = 60_000L
    const val MAX_MS = 365L * 24 * 60 * 60 * 1000

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    fun toMillis(count: Int, unit: DelayUnit): Long {
        val unitMs = when (unit) {
            DelayUnit.MINUTES -> MINUTE_MS
            DelayUnit.HOURS -> HOUR_MS
            DelayUnit.DAYS -> DAY_MS
        }
        return count.toLong() * unitMs
    }

    fun isAllowed(count: Int, unit: DelayUnit): Boolean {
        if (count < 1) return false
        val ms = toMillis(count, unit)
        return ms in MIN_MS..MAX_MS
    }

    fun fromMillis(ms: Long): DelayAmount {
        val safe = ms.coerceIn(MIN_MS, MAX_MS)
        if (safe % DAY_MS == 0L) return DelayAmount((safe / DAY_MS).toInt(), DelayUnit.DAYS)
        if (safe % HOUR_MS == 0L) return DelayAmount((safe / HOUR_MS).toInt(), DelayUnit.HOURS)
        return DelayAmount((safe / MINUTE_MS).toInt().coerceAtLeast(1), DelayUnit.MINUTES)
    }
}

data class ScheduleState(
    val ease: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val dueAtEpochMs: Long,
    val lastReviewedAtEpochMs: Long?,
) {
    companion object {
        fun fresh(nowEpochMs: Long) = ScheduleState(
            ease = Scheduler.DEFAULT_EASE,
            intervalDays = 0,
            repetitions = 0,
            lapses = 0,
            dueAtEpochMs = nowEpochMs,
            lastReviewedAtEpochMs = null,
        )
    }
}

object Scheduler {
    const val MIN_EASE = 1.3
    const val MAX_EASE = 3.0
    const val DEFAULT_EASE = 2.5
    const val MAX_INTERVAL_DAYS = 365

    private const val DAY_MS = 86_400_000L

    fun review(
        state: ScheduleState,
        rating: Rating,
        nowEpochMs: Long,
        delays: RepeatDelays = RepeatDelays.Default,
    ): ScheduleState {
        val wait = delays.sanitized().forRating(rating)
        return state.copy(
            intervalDays = (wait / DAY_MS).toInt(),
            repetitions = if (rating == Rating.BAD) 0 else state.repetitions + 1,
            lapses = if (rating == Rating.BAD) state.lapses + 1 else state.lapses,
            dueAtEpochMs = nowEpochMs + wait,
            lastReviewedAtEpochMs = nowEpochMs,
        )
    }
}

object SessionQueue {
    fun afterGrade(ids: List<Long>, currentId: Long): List<Long> = ids.filterNot { it == currentId }
}
