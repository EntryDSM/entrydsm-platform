package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogPageResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetClientLogsUseCase
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ClientLogController(
    private val getClientLogsUseCase: GetClientLogsUseCase,
) {
    @GetMapping("/api/monitor/v11/logs/client")
    fun logs(
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) cursor: String?,
    ): ApiResponse<ClientLogPageResponse> {
        val levels = level?.split(",")?.map {
            runCatching { LogLevel.valueOf(it.trim()) }.getOrElse { throw MonitorDomainException(ErrorCode.INVALID_PAYLOAD) }
        }?.toSet()
        val decodedCursor = cursor?.let { Cursor.decode(it) }
        val page = getClientLogsUseCase.getLogs(levels, from, to, size, decodedCursor)
        return ApiResponse(data = page.toResponse())
    }
}
