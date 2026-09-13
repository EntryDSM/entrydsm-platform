package hs.kr.entrydsm.observability.application.port.out

import hs.kr.entrydsm.observability.domain.enum.DeviceType
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import java.time.Instant

interface SessionStorePort {
    fun enter(sessionId: String, service: ServiceName, deviceType: DeviceType, now: Instant)

    /** @return 세션이 존재하지 않으면 false */
    fun heartbeat(sessionId: String, service: ServiceName, now: Instant): Boolean

    /** @return 세션이 존재하지 않으면 false */
    fun leave(sessionId: String, service: ServiceName, now: Instant): Boolean

    /** service가 null이면 서비스 중복 제거된 전체(TOTAL) 동시 접속자 수를 반환한다. */
    fun concurrentUsers(service: ServiceName?, now: Instant, windowSeconds: Long): Int

    fun totalVisitors(): Long

    fun avgSessionDurationSeconds(): Long

    fun deviceBreakdown(): Map<DeviceType, Long>

    fun sampleConcurrency(now: Instant, windowSeconds: Long)

    fun concurrentMax(): Int

    fun concurrentAvg(): Int
}
