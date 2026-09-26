package pl.restrictor.kartka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun goodOnANewCardWaitsOneDay() {
        val next = Scheduler.review(ScheduleState.fresh(now), Rating.GOOD, now)
        assertEquals(1, next.intervalDays)
        assertEquals(1, next.repetitions)
        assertEquals(0, next.lapses)
        assertEquals(2.5, next.ease, 0.001)
        assertEquals(now + day, next.dueAtEpochMs)
    }

    @Test
    fun secondGoodWaitsThreeDays() {
        val once = Scheduler.review(ScheduleState.fresh(now), Rating.GOOD, now)
        val twice = Scheduler.review(once, Rating.GOOD, once.dueAtEpochMs)
        assertEquals(3, twice.intervalDays)
        assertEquals(2, twice.repetitions)
    }

    @Test
    fun laterGoodGrowsByEaseAndCapsAt180Days() {
        val grown = Scheduler.review(
            ScheduleState(2.5, 3, 2, 0, now, null),
            Rating.GOOD,
            now,
        )
        assertEquals(8, grown.intervalDays)

        val capped = Scheduler.review(
            ScheduleState(2.5, 100, 4, 0, now, null),
            Rating.GOOD,
            now,
        )
        assertEquals(180, capped.intervalDays)
        assertEquals(now + 180 * day, capped.dueAtEpochMs)
    }

    @Test
    fun easyStartsAtThreeDaysAndRaisesEase() {
        val next = Scheduler.review(ScheduleState.fresh(now), Rating.EASY, now)
        assertEquals(3, next.intervalDays)
        assertEquals(2.65, next.ease, 0.001)
        assertEquals(1, next.repetitions)
    }

    @Test
    fun easeNeverExceedsThree() {
        val next = Scheduler.review(
            ScheduleState(2.95, 10, 3, 0, now, null),
            Rating.EASY,
            now,
        )
        assertEquals(3.0, next.ease, 0.001)
        assertTrue(next.intervalDays in 1..180)
    }

    @Test
    fun againComesBackSoonAndNeverDropsTheCard() {
        val next = Scheduler.review(
            ScheduleState(2.5, 16, 4, 1, now, now),
            Rating.AGAIN,
            now,
        )
        assertEquals(0, next.intervalDays)
        assertEquals(0, next.repetitions)
        assertEquals(2, next.lapses)
        assertEquals(2.3, next.ease, 0.001)
        assertEquals(now + Scheduler.AGAIN_DELAY_MS, next.dueAtEpochMs)
    }

    @Test
    fun easeNeverDropsBelowTheFloor() {
        val next = Scheduler.review(
            ScheduleState(1.3, 1, 2, 3, now, now),
            Rating.AGAIN,
            now,
        )
        assertEquals(1.3, next.ease, 0.001)
    }

    @Test
    fun againGoesToTheEndSoTheRestOfTheDeckIsSeen() {
        assertEquals(listOf(2L, 3L, 1L), SessionQueue.afterAgain(listOf(1, 2, 3), 1))
        assertEquals(listOf(2L, 1L), SessionQueue.afterAgain(listOf(1, 2), 1))
        assertEquals(listOf(1L), SessionQueue.afterAgain(listOf(1), 1))
        assertEquals(listOf(2L, 3L, 4L, 5L, 1L), SessionQueue.afterAgain(listOf(1, 2, 3, 4, 5), 1))
        assertEquals(listOf(2L, 3L), SessionQueue.afterPass(listOf(1, 2, 3), 1))

        var queue = listOf(1L, 2L, 3L, 4L, 5L)
        val seen = mutableListOf<Long>()
        repeat(queue.size) {
            seen += queue.first()
            queue = SessionQueue.afterAgain(queue, queue.first())
        }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), seen)
    }
}
