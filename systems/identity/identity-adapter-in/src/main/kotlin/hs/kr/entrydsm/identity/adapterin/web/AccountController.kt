package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.BasicInfoResponse
import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/identity/v11/accounts")
class AccountController(
    private val accountPort: AccountPort,
) {
    @DeleteMapping("/me")
    fun deleteMe(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ApiResponse<Unit> {
        accountPort.deleteAccount(DeleteAccountCommand(authorization = authorization))
        return ApiResponse(data = null)
    }

    @GetMapping("/me")
    fun getMe(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ApiResponse<BasicInfoResponse> {
        val result = accountPort.getBasicInfo(ReadAccountCommand(authorization = authorization))
        return ApiResponse(data = result.toResponse())
    }
}
