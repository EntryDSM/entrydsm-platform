package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.request.LoginRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.request.SignupRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.response.AccountResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.UserSummaryResponse
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.AuthTokenResult
import hs.kr.entrydsm.identity.application.security.AuthenticatedUser
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import java.net.URI
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(AuthEndpointPaths.BASE)
class AuthController(
    private val authPort: AuthPort,
) {
    @PostMapping(AuthEndpointPaths.SIGNUP_PATH)
    fun signup(
        @Valid @RequestBody request: SignupRequest,
    ): ResponseEntity<ApiResponse<AccountResponse>> {
        val result = authPort.signup(
            SignupCommand(
                password = request.password,
                name = request.name,
                phone = request.phone,
                birthdate = request.birthdate,
                signupType = request.signupType,
            )
        )
        return ResponseEntity
            .created(URI.create("/api/identity/v11/accounts/me"))
            .body(ApiResponse(data = result.toResponse()))
    }

    @PostMapping(AuthEndpointPaths.LOGIN_PATH)
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): ResponseEntity<ApiResponse<UserSummaryResponse>> {
        val result = authPort.login(
            LoginCommand(
                loginId = request.loginId,
                password = request.password,
            )
        )
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie(result).toString())
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result).toString())
            .body(ApiResponse(data = result.toUserSummaryResponse()))
    }

    @PostMapping(AuthEndpointPaths.LOGOUT_PATH)
    fun logout(
        authentication: Authentication,
    ): ResponseEntity<ApiResponse<Unit>> {
        val principal = authentication.principal as? AuthenticatedUser
            ?: throw IllegalArgumentException("Authenticated principal is not validated.")
        authPort.logout(LogoutCommand(userId = principal.userId))
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, expiredCookie("access_token").toString())
            .header(HttpHeaders.SET_COOKIE, expiredCookie("refresh_token").toString())
            .body(ApiResponse(data = null))
    }

    @PostMapping(AuthEndpointPaths.TOKEN_PATH)
    fun refreshToken(
        @CookieValue("refresh_token", required = false) refreshToken: String?,
    ): ResponseEntity<ApiResponse<UserSummaryResponse>> {
        val result = authPort.refreshToken(RefreshTokenCommand(refreshToken = refreshToken))
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie(result).toString())
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result).toString())
            .body(ApiResponse(data = result.toUserSummaryResponse()))
    }

    private fun accessTokenCookie(result: AuthTokenResult): ResponseCookie =
        ResponseCookie.from("access_token", result.accessToken.value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(7200)
            .build()

    private fun refreshTokenCookie(result: AuthTokenResult): ResponseCookie =
        ResponseCookie.from("refresh_token", result.refreshToken.value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(604800)
            .build()

    private fun expiredCookie(name: String): ResponseCookie =
        ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build()

    private fun AuthTokenResult.toUserSummaryResponse(): UserSummaryResponse =
        UserSummaryResponse(
            userId = "${JwtTokenGenerator.USER_PRINCIPAL_PREFIX}$userId",
            role = role.name,
            status = status,
        )

}
