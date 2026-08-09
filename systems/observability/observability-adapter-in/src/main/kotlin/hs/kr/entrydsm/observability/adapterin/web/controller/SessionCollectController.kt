package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.adapterin.web.dto.request.SessionEventRequest
import hs.kr.entrydsm.observability.adapterin.web.dto.response.SessionEventResponse
import hs.kr.entrydsm.observability.application.port.`in`.RecordSessionEventUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class SessionCollectController(
    private val recordSessionEventUseCase: RecordSessionEventUseCase,
) {
    @PostMapping("/api/monitor/v11/collect/session")
    fun collect(
        @Valid @RequestBody request: SessionEventRequest,
        @RequestHeader(value = "User-Agent", required = false) userAgent: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<ApiResponse<SessionEventResponse>> {
        val result = recordSessionEventUseCase.record(
            event = request.event,
            sessionId = request.sessionId,
            service = request.service,
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
