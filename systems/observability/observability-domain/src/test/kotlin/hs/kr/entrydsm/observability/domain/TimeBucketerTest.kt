package hs.kr.entrydsm.observability.domain

import hs.kr.entrydsm.observability.domain.service.TimeBucketer
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeBucketerTest {
    @Test
    fun resolvesKnownIntervals() {
        assertEquals(Duration.ofMinutes(5), TimeBucketer.durationOf("5m"))
        assertEquals(Duration.ofHours(1), TimeBucketer.durationOf("1h"))
        assertNull(TimeBucketer.durationOf("2h"))
    }

    @Test
    fun bucketStartsCoverWholeRangeWithoutOverflow() {
        val from = Instant.parse("2026-07-28T00:00:00Z")
        val to = Instant.parse("2026-07-28T02:00:00Z")

        val buckets = TimeBucketer.bucketStarts(from, to, Duration.ofHours(1))

        assertEquals(listOf(Instant.parse("2026-07-28T00:00:00Z"), Instant.parse("2026-07-28T01:00:00Z")), buckets)
    }
}
