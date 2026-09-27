package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationPeriodClosedException
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import java.time.Instant

/**
 * 원서 접수 기간이 아니면 막는다. 시작·끝 시각도 기간에 들고, 일정이 등록되지 않았으면 기간이 아니다.
 */
fun ApplicationPeriodReader.requireOpen(now: Instant = Instant.now()) {
    if (read()?.contains(now) != true) throw ApplicationPeriodClosedException()
}
