package hs.kr.entrydsm.gateway.adapterin.web

import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/identity/v11/auth")
class CsrfController {

    @GetMapping("/csrf")
    fun csrf(
        exchange: ServerWebExchange,
    ): Mono<ApiResponse<CsrfTokenResponse>> {
        val csrfToken = exchange.getAttribute<Mono<CsrfToken>>(
            CsrfToken::class.java.name,
        ) ?: return Mono.error(
            IllegalStateException("CSRF token attribute not found"),
        )

        return csrfToken.map {
            ApiResponse(
                data = CsrfTokenResponse(it.token),
            )
        }
    }
}

data class CsrfTokenResponse(
    val token: String,
)

data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T?,
    val error: ErrorDetail? = null,
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val status: Int,
)