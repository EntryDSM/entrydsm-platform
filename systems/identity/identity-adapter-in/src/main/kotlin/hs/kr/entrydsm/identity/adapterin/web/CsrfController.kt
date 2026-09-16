package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SPA 가 CSRF 토큰을 받아 가는 곳이다. gateway 가 직접 처리하던 경로를 그대로 옮겼다.
 *
 * 쿠키는 HttpOnly 라 스크립트가 읽지 못하므로 응답 본문으로 값을 준다. SPA 는 이 값을 `X-XSRF-TOKEN` 으로 돌려보낸다.
 */
@RestController
@RequestMapping(AuthEndpointPaths.BASE)
class CsrfController {
    @GetMapping(AuthEndpointPaths.CSRF_PATH)
    fun csrf(csrfToken: CsrfToken): ApiResponse<CsrfTokenResponse> =
        ApiResponse(data = CsrfTokenResponse(csrfToken.token))
}

data class CsrfTokenResponse(
    val token: String,
)
