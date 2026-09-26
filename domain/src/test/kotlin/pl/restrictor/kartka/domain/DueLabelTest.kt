package pl.restrictor.kartka.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DueLabelTest {
    private val now = 1_700_000_000_000L

    @Test
    fun labelsMatchHowFarAwayTheCardIs() {
        assertEquals(DueText.Now, DueLabel.of(now, now))
        assertEquals(DueText.Now, DueLabel.of(now - 1, now))
        assertEquals(DueText.Minutes(10), DueLabel.of(now + 10 * 60_000L, now))
        assertEquals(DueText.Hours(5), DueLabel.of(now + 5 * 3_600_000L, now))
        assertEquals(DueText.Tomorrow, DueLabel.of(now + 20 * 3_600_000L, now))
        assertEquals(DueText.Days(16), DueLabel.of(now + 16 * 86_400_000L, now))
    }
}
