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

    /** 버킷 목록을 만들지 않고 개수만 센다. 범위 검증에서 큰 리스트를 만들었다 버리지 않기 위한 것. */
    fun bucketCount(from: Instant, to: Instant, interval: Duration): Long {
        require(!interval.isZero && !interval.isNegative) { "interval must be positive: $interval" }
        if (!from.isBefore(to)) return 0
        val span = Duration.between(from, to).toMillis()
        val step = interval.toMillis()
        return (span + step - 1) / step
    }

    /** points의 t는 버킷의 시작 시각이다. */
    fun bucketStarts(from: Instant, to: Instant, interval: Duration): List<Instant> {
        require(!interval.isZero && !interval.isNegative) { "interval must be positive: $interval" }
        val starts = mutableListOf<Instant>()
        var cursor = from
        while (cursor.isBefore(to)) {
            starts.add(cursor)
            cursor = cursor.plus(interval)
        }
        return starts
    }
}
