package pl.restrictor.kartka.domain

import kotlin.math.round

enum class Rating {
    AGAIN,
    GOOD,
    EASY,
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
    const val MAX_INTERVAL_DAYS = 180
    const val AGAIN_DELAY_MS = 10 * 60 * 1000L

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun review(state: ScheduleState, rating: Rating, nowEpochMs: Long): ScheduleState {
        return when (rating) {
            Rating.AGAIN -> state.copy(
                ease = (state.ease - 0.2).coerceIn(MIN_EASE, MAX_EASE),
                intervalDays = 0,
                repetitions = 0,
                lapses = state.lapses + 1,
                dueAtEpochMs = nowEpochMs + AGAIN_DELAY_MS,
                lastReviewedAtEpochMs = nowEpochMs,
            )
            Rating.GOOD -> {
                val days = goodInterval(state).coerceIn(1, MAX_INTERVAL_DAYS)
                state.copy(
                    intervalDays = days,
                    repetitions = state.repetitions + 1,
                    dueAtEpochMs = nowEpochMs + days * DAY_MS,
                    lastReviewedAtEpochMs = nowEpochMs,
                )
            }
            Rating.EASY -> {
                val ease = (state.ease + 0.15).coerceIn(MIN_EASE, MAX_EASE)
                val days = easyInterval(state, ease).coerceIn(1, MAX_INTERVAL_DAYS)
                state.copy(
                    ease = ease,
                    intervalDays = days,
                    repetitions = state.repetitions + 1,
                    dueAtEpochMs = nowEpochMs + days * DAY_MS,
                    lastReviewedAtEpochMs = nowEpochMs,
                )
            }
        }
    }

    private fun goodInterval(state: ScheduleState): Int = when {
        state.repetitions <= 0 -> 1
        state.repetitions == 1 -> 3
        else -> round(state.intervalDays * state.ease).toInt().coerceAtLeast(1)
    }

    private fun easyInterval(state: ScheduleState, ease: Double): Int = when {
        state.repetitions <= 0 -> 3
        state.repetitions == 1 -> round(3.0 * ease).toInt().coerceAtLeast(4)
        else -> round(state.intervalDays * ease * 1.3).toInt().coerceAtLeast(state.intervalDays + 1)
    }
}

object SessionQueue {
    fun afterAgain(ids: List<Long>, currentId: Long): List<Long> {
        val rest = ids.filterNot { it == currentId }
        val index = minOf(2, rest.size)
        return buildList {
            addAll(rest)
            add(index, currentId)
        }
    }

    fun afterPass(ids: List<Long>, currentId: Long): List<Long> = ids.filterNot { it == currentId }
}
