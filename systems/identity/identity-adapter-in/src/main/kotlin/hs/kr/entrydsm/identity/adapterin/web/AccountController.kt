package hs.kr.entrydsm.identity.adapterin.web

import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.BasicInfoResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.UserSummaryResponse
import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/identity/v11/accounts")
class AccountController(
    private val accountPort: AccountPort,
) {
    @DeleteMapping("/me")
    fun deleteMe(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser? = null,
    ): ApiResponse<Unit> {
        accountPort.deleteAccount(
            DeleteAccountCommand(userId = authenticatedUser?.userId),
        )
        return ApiResponse(data = null)
    }

    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser? = null,
    ): ApiResponse<BasicInfoResponse> {
        val result = accountPort.getBasicInfo(
            ReadAccountCommand(userId = authenticatedUser?.userId),
        )
        return ApiResponse(data = result.toResponse())
    }

    @GetMapping("/me/authority")
    fun getMyAuthority(
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser? = null,
    ): ApiResponse<UserSummaryResponse> {
        val result = accountPort.getAuthority(
            ReadAccountCommand(userId = authenticatedUser?.userId),
        )
        return ApiResponse(data = result.toResponse())
    }

    @PatchMapping("/sensitive-agree")
    fun agreeSensitiveInformation(
        @Valid @RequestBody request: SensitiveAgreeRequest,
        @AuthenticationPrincipal authenticatedUser: AuthenticatedUser? = null,
    ): ResponseEntity<ApiResponse<SensitiveAgreeResponse>> {
        val result = accountPort.agreeSensitiveInformation(
            ReadAccountCommand(userId = authenticatedUser?.userId),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(data = SensitiveAgreeResponse(result.isSensitiveAgree, result.updatedAt)),
        )
    }
}

data class SensitiveAgreeRequest(
    @field:AssertTrue
    @JsonProperty("is_sensitive_agree")
    val isSensitiveAgree: Boolean,
)

data class SensitiveAgreeResponse(
    @JsonProperty("is_sensitive_agree")
    val isSensitiveAgree: Boolean,
    val updatedAt: Instant,
)
