package hs.kr.entrydsm.observability.domain.service

import hs.kr.entrydsm.observability.domain.enum.ServiceStatus

/**
 * 서비스 헬스체크 응답을 문서화된 판정 기준(UP/DEGRADED/DOWN)에 따라 분류합니다.
 *
 * - UP: 헬스체크 성공 + 응답 500ms 미만 + 모든 의존성 정상
 * - DEGRADED: 응답 500ms 이상 또는 일부 의존성 장애
 * - DOWN: 타임아웃 또는 연결 실패
 */
object HealthStatusClassifier {
    private const val SLOW_RESPONSE_THRESHOLD_MS = 500

    fun classify(responseTimeMs: Long?, allDependenciesUp: Boolean): ServiceStatus = when {
        responseTimeMs == null -> ServiceStatus.DOWN
        responseTimeMs >= SLOW_RESPONSE_THRESHOLD_MS || !allDependenciesUp -> ServiceStatus.DEGRADED
        else -> ServiceStatus.UP
    }

    /** overall은 하위 서비스 중 가장 나쁜 상태를 따른다. */
    fun overall(statuses: Collection<ServiceStatus>): ServiceStatus = when {
        statuses.isEmpty() -> ServiceStatus.DOWN
        statuses.any { it == ServiceStatus.DOWN } -> ServiceStatus.DOWN
        statuses.any { it == ServiceStatus.DEGRADED } -> ServiceStatus.DEGRADED
        else -> ServiceStatus.UP
    }
}
