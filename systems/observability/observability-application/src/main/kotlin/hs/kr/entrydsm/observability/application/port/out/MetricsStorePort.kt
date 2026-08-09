package hs.kr.entrydsm.observability.application.port.out

import java.time.Instant

interface MetricsStorePort {
    /** 세션의 ENTER/HEARTBEAT 시각을 5분 단위 버킷에 기록한다. */
    fun recordVisitor(sessionId: String, at: Instant)

    /** [from, to) 구간의 고유 세션 수. */
    fun visitorCount(from: Instant, to: Instant): Long
}
