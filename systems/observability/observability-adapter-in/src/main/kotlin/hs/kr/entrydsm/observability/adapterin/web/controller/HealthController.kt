package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServiceHealthResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetServiceHealthUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val getServiceHealthUseCase: GetServiceHealthUseCase,
) {
    @GetMapping("/api/monitor/v11/health")
    fun health(): ApiResponse<ServiceHealthResponse> =
        ApiResponse(data = getServiceHealthUseCase.getHealth().toResponse())
}
