package pl.restrictor.kartka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val day = 86_400_000L

    @Test
    fun defaultsAreOneHourOneDayAndOneWeek() {
        assertEquals(hour, RepeatDelays.Default.badMs)
        assertEquals(day, RepeatDelays.Default.mediumMs)
        assertEquals(7 * day, RepeatDelays.Default.goodMs)
    }

    @Test
    fun badWaitsTheConfiguredTimeAndStaysInTheDeck() {
        val next = Scheduler.review(
            ScheduleState(2.5, 16, 4, 1, now, now),
            Rating.BAD,
            now,
            RepeatDelays(2 * hour, day, 7 * day),
        )
        assertEquals(now + 2 * hour, next.dueAtEpochMs)
        assertEquals(0, next.intervalDays)
        assertEquals(0, next.repetitions)
        assertEquals(2, next.lapses)
        assertEquals(2.5, next.ease, 0.001)
        assertTrue(next.dueAtEpochMs > now)
    }

    @Test
    fun mediumAndGoodUseTheirOwnDelays() {
        val fresh = ScheduleState.fresh(now)
        val medium = Scheduler.review(fresh, Rating.MEDIUM, now)
        assertEquals(now + day, medium.dueAtEpochMs)
        assertEquals(1, medium.intervalDays)
        assertEquals(1, medium.repetitions)
        assertEquals(0, medium.lapses)

        val good = Scheduler.review(fresh, Rating.GOOD, now)
        assertEquals(now + 7 * day, good.dueAtEpochMs)
        assertEquals(7, good.intervalDays)
        assertEquals(1, good.repetitions)
    }

    @Test
    fun previousIntervalDoesNotChangeTheWait() {
        val grown = ScheduleState(2.5, 100, 8, 0, now, null)
        val next = Scheduler.review(grown, Rating.MEDIUM, now)
        assertEquals(now + day, next.dueAtEpochMs)
    }

    @Test
    fun delaysOutsideTheAllowedRangeAreBroughtBackIn() {
        val wild = RepeatDelays(1L, day, 800 * day)
        val next = Scheduler.review(ScheduleState.fresh(now), Rating.BAD, now, wild)
        assertEquals(now + DelayAmounts.MIN_MS, next.dueAtEpochMs)
        val far = Scheduler.review(ScheduleState.fresh(now), Rating.GOOD, now, wild)
        assertEquals(now + DelayAmounts.MAX_MS, far.dueAtEpochMs)
    }

    @Test
    fun delayAmountsRoundTripTheDefaults() {
        assertEquals(DelayAmount(1, DelayUnit.HOURS), DelayAmounts.fromMillis(hour))
        assertEquals(DelayAmount(1, DelayUnit.DAYS), DelayAmounts.fromMillis(day))
        assertEquals(DelayAmount(7, DelayUnit.DAYS), DelayAmounts.fromMillis(7 * day))
        assertTrue(DelayAmounts.isAllowed(1, DelayUnit.HOURS))
        assertFalse(DelayAmounts.isAllowed(0, DelayUnit.DAYS))
        assertFalse(DelayAmounts.isAllowed(366, DelayUnit.DAYS))
    }

    @Test
    fun everyGradeLeavesTheSession() {
        assertEquals(listOf(2L, 3L, 4L), SessionQueue.afterGrade(listOf(1, 2, 3, 4), 1))
        assertEquals(emptyList<Long>(), SessionQueue.afterGrade(listOf(1), 1))
    }
}
