package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.StorageUsageResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetStorageUsageUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ResourceController(
    private val getStorageUsageUseCase: GetStorageUsageUseCase,
) {
    @GetMapping("/api/monitor/v11/resources")
    fun resources(): ApiResponse<StorageUsageResponse> = ApiResponse(data = getStorageUsageUseCase.getUsage().toResponse())
}
