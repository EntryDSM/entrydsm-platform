package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.DashboardSnapshotResult

fun interface GetDashboardSnapshotUseCase {
    /** @param round 생략 시 진행 중인 회차 */
    fun getSnapshot(round: String?): DashboardSnapshotResult
}
