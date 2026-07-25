package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.request.LoginRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.request.PasswordResetRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.request.SignupRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.response.AccountResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.UserSummaryResponse
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.command.LoginCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.RefreshTokenCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.SignupCommand
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import java.net.URI
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/identity/v11/auth")
class AuthController(
    private val authPort: AuthPort,
    private val jwtTokenGenerator: JwtTokenGenerator,
) {
    @PostMapping("/signup")
    fun signup(
        @RequestBody request: SignupRequest,
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

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
    ): ResponseEntity<ApiResponse<UserSummaryResponse>> {
        val result = authPort.login(
            LoginCommand(
                loginId = request.loginId,
                password = request.password,
            )
        )
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie(result.userId).toString())
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.userId).toString())
            .body(ApiResponse(data = result.toResponse()))
    }

    @PostMapping("/logout")
    fun logout(
        authentication: Authentication,
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = authentication.name.removePrefix(USER_PRINCIPAL_PREFIX).toLongOrNull()
            ?: throw IllegalArgumentException("Authenticated principal must contain a numeric user id.")
        authPort.logout(LogoutCommand(userId = userId))
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, expiredCookie("access_token").toString())
            .header(HttpHeaders.SET_COOKIE, expiredCookie("refresh_token").toString())
            .body(ApiResponse(data = null))
    }

    @PostMapping("/token")
    fun refreshToken(
        @CookieValue("refresh_token", required = false) refreshToken: String?,
    ): ResponseEntity<ApiResponse<UserSummaryResponse>> {
        val result = authPort.refreshToken(RefreshTokenCommand(refreshToken = refreshToken))
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessTokenCookie(result.userId).toString())
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.userId).toString())
            .body(ApiResponse(data = result.toResponse()))
    }

    @PatchMapping("/password-reset")
    fun resetPassword(
        @RequestBody request: PasswordResetRequest,
    ): ApiResponse<Unit> {
        authPort.resetPassword(
            PasswordResetCommand(
                loginId = request.loginId,
                name = request.name,
                birthdate = request.birthdate,
                newPassword = request.newPassword,
            )
        )
        return ApiResponse(data = null)
    }

    private fun accessTokenCookie(userId: Long): ResponseCookie =
        ResponseCookie.from("access_token", jwtTokenGenerator.generateAccessToken(principal(userId)).value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(7200)
            .build()

    private fun refreshTokenCookie(userId: Long): ResponseCookie =
        ResponseCookie.from("refresh_token", jwtTokenGenerator.generateRefreshToken(principal(userId)).value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(604800)
            .build()

    private fun expiredCookie(name: String): ResponseCookie =
        ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(0)
            .build()

    private fun principal(userId: Long): String = "$USER_PRINCIPAL_PREFIX$userId"

    companion object {
        private const val USER_PRINCIPAL_PREFIX = "user_"
    }
}
