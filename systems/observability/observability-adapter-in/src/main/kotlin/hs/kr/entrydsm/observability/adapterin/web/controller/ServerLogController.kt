package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ServerLogPageResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetServerLogsUseCase
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ServerLogController(
    private val getServerLogsUseCase: GetServerLogsUseCase,
) {
    @GetMapping("/api/monitor/v11/logs/server")
    fun logs(
        @RequestParam(required = false) service: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) cursor: String?,
    ): ApiResponse<ServerLogPageResponse> {
        val resolvedService = service?.let {
            runCatching { ServiceName.valueOf(it.trim()) }.getOrElse { throw MonitorDomainException(ErrorCode.INVALID_SERVICE) }
        }
        val decodedCursor = cursor?.let { Cursor.decode(it) }
        val page = getServerLogsUseCase.getLogs(resolvedService, status, from, to, size, decodedCursor)
        return ApiResponse(data = page.toResponse())
    }
}
