package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.request.ClientLogCollectRequest
import hs.kr.entrydsm.observability.adapterin.web.dto.response.ClientLogAcceptResponse
import hs.kr.entrydsm.observability.application.port.`in`.ClientLogItem
import hs.kr.entrydsm.observability.application.port.`in`.RecordClientLogUseCase
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

private const val MAX_PAYLOAD_BYTES = 64 * 1024

@RestController
class ClientLogCollectController(
    private val recordClientLogUseCase: RecordClientLogUseCase,
) {
    @PostMapping("/api/monitor/v11/collect/client-log")
    fun collect(
        @Valid @RequestBody request: ClientLogCollectRequest,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<ClientLogAcceptResponse>> {
        if (httpRequest.contentLengthLong > MAX_PAYLOAD_BYTES) {
            throw MonitorDomainException(ErrorCode.PAYLOAD_TOO_LARGE)
        }
        val result = recordClientLogUseCase.record(
            sessionId = request.sessionId,
            logs = request.logs.map {
                ClientLogItem(
                    level = it.level,
                    source = it.source,
                    message = it.message,
                    stack = it.stack,
                    pageUrl = it.pageUrl,
                    occurredAt = it.occurredAt,
                )
            },
            userAgent = userAgent,
            clientIp = clientIp(httpRequest),
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse(data = result.toResponse()))
    }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")
            ?.substringBefore(",")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: request.remoteAddr
}
