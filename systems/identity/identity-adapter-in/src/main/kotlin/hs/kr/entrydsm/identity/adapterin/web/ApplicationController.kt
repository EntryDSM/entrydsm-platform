package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.ApplicationResultResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.ApplicationStatusResponse
import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadApplicationCommand
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/identity/v11/applications")
class ApplicationController(
    private val applicationPort: ApplicationPort,
) {
    @GetMapping("/status")
    fun getStatus(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser? = null,
    ): ApiResponse<ApplicationStatusResponse> {
        val result = applicationPort.getApplicationStatus(
            ReadApplicationCommand(userId = authenticatedUser?.userId),
        )
        return ApiResponse(data = result.toResponse())
    }

    @GetMapping("/result")
    fun getResult(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser? = null,
    ): ApiResponse<ApplicationResultResponse> {
        val result = applicationPort.getApplicationResult(
            ReadApplicationCommand(userId = authenticatedUser?.userId),
        )
        return ApiResponse(data = result.toResponse())
    }
}
