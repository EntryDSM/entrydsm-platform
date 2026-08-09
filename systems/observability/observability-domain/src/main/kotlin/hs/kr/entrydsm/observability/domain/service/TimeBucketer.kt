package hs.kr.entrydsm.observability.domain.service

import java.time.Duration
import java.time.Instant

/** 시간대별 지표 조회의 interval 문자열 <-> Duration 변환과 버킷 시작 시각 목록 생성을 담당한다. */
object TimeBucketer {
    private val INTERVALS = mapOf(
        "5m" to Duration.ofMinutes(5),
        "30m" to Duration.ofMinutes(30),
        "1h" to Duration.ofHours(1),
        "1d" to Duration.ofDays(1),
    )

    fun durationOf(interval: String): Duration? = INTERVALS[interval]

    /** points의 t는 버킷의 시작 시각이다. */
    fun bucketStarts(from: Instant, to: Instant, interval: Duration): List<Instant> {
        val starts = mutableListOf<Instant>()
        var cursor = from
        while (cursor.isBefore(to)) {
            starts.add(cursor)
            cursor = cursor.plus(interval)
        }
        return starts
    }
}
