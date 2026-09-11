package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayServiceProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import hs.kr.entrydsm.gateway.domain.GatewayService
import io.netty.util.NetUtil
import java.net.InetAddress
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class GatewayAccessGlobalFilter(
    properties: GatewayServiceProperties,
    private val responseWriter: GatewayErrorResponseWriter,
    private val objectMapper: ObjectMapper,
) : GlobalFilter, Ordered {
    private val identityClient = WebClient.builder().baseUrl(properties.identity.toString()).build()

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val request = exchange.request
        val sanitizedExchange = exchange.mutate()
            .request(
                request.mutate()
                    .headers { headers ->
                        TRUSTED_HEADERS.forEach(headers::remove)
                        clientIp(request)?.let { headers.set(CLIENT_IP_HEADER, it) }
                    }
                    .build(),
            )
            .build()

        if (request.path.value().startsWith(GatewayService.IDENTITY.pathPrefix)) {
            return chain.filter(sanitizedExchange)
        }

        val bearerToken = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) && it.length > BEARER_PREFIX.length }
        val accessTokenCookie = request.cookies.getFirst(ACCESS_TOKEN_COOKIE)?.value
        if (bearerToken == null && accessTokenCookie.isNullOrBlank()) {
            return chain.filter(sanitizedExchange)
        }
        if (bearerToken == null && requiresCsrf(request.method) && !hasValidCsrfToken(exchange)) {
            return responseWriter.write(exchange, HttpStatus.FORBIDDEN, "CSRF_INVALID")
        }

        return authenticate(
            authorization = request.headers.getFirst(HttpHeaders.AUTHORIZATION),
            cookie = request.headers.getFirst(HttpHeaders.COOKIE),
        ).flatMap { result ->
            when (result) {
                is AuthenticationResult.Success -> chain.filter(
                    sanitizedExchange.mutate()
                        .request(
                            sanitizedExchange.request.mutate()
                                .headers { headers ->
                                    headers.set(USER_ID_HEADER, result.userId.toString())
                                    headers.set(USER_ROLE_HEADER, result.role)
                                    headers.set(APPLICATION_USER_ID_HEADER, result.userId.toString())
                                    headers.set(SENSITIVE_AGREE_HEADER, result.isSensitiveAgree.toString())
                                }
                                .build(),
                        )
                        .build(),
                )

                AuthenticationResult.Unauthorized ->
                    responseWriter.write(exchange, HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED")

                AuthenticationResult.Unavailable ->
                    responseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE")
            }
        }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    private fun authenticate(authorization: String?, cookie: String?): Mono<AuthenticationResult> =
        identityClient.get()
            .uri(AUTHORITY_PATH)
            .headers { headers ->
                authorization?.let { headers.set(HttpHeaders.AUTHORIZATION, it) }
                cookie?.let { headers.set(HttpHeaders.COOKIE, it) }
            }
            .exchangeToMono { response ->
                when {
                    response.statusCode().is2xxSuccessful -> response.bodyToMono(String::class.java)
                        .map(::parseAuthenticationResult)

                    response.statusCode().value() in 400..499 -> Mono.just(AuthenticationResult.Unauthorized)
                    else -> Mono.just(AuthenticationResult.Unavailable)
                }
            }
            .onErrorReturn(AuthenticationResult.Unavailable)

    private fun parseAuthenticationResult(body: String): AuthenticationResult = runCatching {
        val data = objectMapper.readTree(body).path("data")
        AuthorityData(
            userId = data.path("userId").asText(),
            role = data.path("role").asText(),
            isSensitiveAgree = data.path("isSensitiveAgree").asBoolean(false),
        ).toAuthenticationResult()
    }.getOrDefault(AuthenticationResult.Unavailable)

    private fun AuthorityData.toAuthenticationResult(): AuthenticationResult {
        val id = userId.removePrefix(USER_PRINCIPAL_PREFIX).toLongOrNull()
            ?.takeIf { it > 0 }
            ?: return AuthenticationResult.Unavailable
        if (role.isBlank()) return AuthenticationResult.Unavailable
        return AuthenticationResult.Success(id, role, isSensitiveAgree)
    }

    private fun hasValidCsrfToken(exchange: ServerWebExchange): Boolean {
        val cookieToken = exchange.request.cookies.getFirst(CSRF_COOKIE)?.value
        val headerToken = exchange.request.headers.getFirst(CSRF_HEADER)
        return !cookieToken.isNullOrBlank() && cookieToken == headerToken
    }

    private fun requiresCsrf(method: HttpMethod): Boolean = method !in SAFE_METHODS

    /**
     * 게이트웨이는 X-Forwarded-*를 다운스트림에 넘기지 않으므로(trusted-proxies 미설정) 판정한 클라이언트 IP를 X-Real-IP로 따로 넘긴다.
     * 직접 붙은 상대가 내부 주소(ALB 같은 프록시)일 때만 X-Forwarded-For를 오른쪽부터 읽어 내부 주소가 아닌 첫 값을 쓴다.
     * 그 왼쪽 값과 공인 IP로 직접 붙은 요청의 X-Forwarded-For는 클라이언트가 위조할 수 있어 쓰지 않는다.
     * ponytail: 사설 IPv4·루프백·링크로컬만 내부 프록시로 본다. 공인 IP 프록시(CloudFront 등)가 앞에 붙으면 신뢰 목록 설정을 추가한다.
     */
    private fun clientIp(request: ServerHttpRequest): String? {
        val peer = request.remoteAddress?.address ?: return null
        if (!peer.isInternal()) return peer.hostAddress
        return request.headers.getValuesAsList(FORWARDED_FOR_HEADER)
            .lastOrNull { NetUtil.createInetAddressFromIpAddressString(it)?.isInternal() != true }
            ?: peer.hostAddress
    }

    private fun InetAddress.isInternal(): Boolean = isSiteLocalAddress || isLoopbackAddress || isLinkLocalAddress

    private sealed interface AuthenticationResult {
        data class Success(val userId: Long, val role: String, val isSensitiveAgree: Boolean) : AuthenticationResult

        data object Unauthorized : AuthenticationResult

        data object Unavailable : AuthenticationResult
    }

    private data class AuthorityData(
        val userId: String = "",
        val role: String = "",
        val isSensitiveAgree: Boolean = false,
    )

    private companion object {
        const val AUTHORITY_PATH = "/api/identity/v11/accounts/me/authority"
        const val ACCESS_TOKEN_COOKIE = "access_token"
        const val CSRF_COOKIE = "XSRF-TOKEN"
        const val CSRF_HEADER = "X-XSRF-TOKEN"
        const val BEARER_PREFIX = "Bearer "
        const val USER_PRINCIPAL_PREFIX = "user_"
        const val USER_ID_HEADER = "X-User-Id"
        const val USER_ROLE_HEADER = "X-User-Role"
        const val APPLICATION_USER_ID_HEADER = "user-id"
        const val SENSITIVE_AGREE_HEADER = "X-Sensitive-Agree"
        const val CLIENT_IP_HEADER = "X-Real-IP"
        const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
        val TRUSTED_HEADERS = setOf(
            USER_ID_HEADER,
            USER_ROLE_HEADER,
            APPLICATION_USER_ID_HEADER,
            SENSITIVE_AGREE_HEADER,
            CLIENT_IP_HEADER,
        )
        val SAFE_METHODS = setOf(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE)
    }
}
