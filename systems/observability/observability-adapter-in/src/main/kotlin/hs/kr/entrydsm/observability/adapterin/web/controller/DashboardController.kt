package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.DashboardSnapshotResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(
    private val getDashboardSnapshotUseCase: GetDashboardSnapshotUseCase,
) {
    @GetMapping("/api/monitor/v11/dashboard")
    fun dashboard(
        @RequestParam(required = false) round: String?,
    ): ApiResponse<DashboardSnapshotResponse> =
        ApiResponse(data = getDashboardSnapshotUseCase.getSnapshot(round).toResponse())
}
