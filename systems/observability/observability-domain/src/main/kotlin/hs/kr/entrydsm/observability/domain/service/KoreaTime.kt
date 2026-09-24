package hs.kr.entrydsm.observability.domain.service

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 모니터링이 내보내는 시각은 한국 시간이다. 저장과 계산은 Instant 그대로 한다. */
object KoreaTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    /** 예: 2026-09-22T00:00:00+09:00 */
    fun format(instant: Instant): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant.atZone(ZONE))
}
