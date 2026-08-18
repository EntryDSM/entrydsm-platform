package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Locale

@Component
class GatewayCorsGlobalFilter(
    properties: GatewayRuntimeProperties,
    private val responseWriter: GatewayErrorResponseWriter,
) : GlobalFilter, Ordered {
    private val allowedOrigins = properties.cors.allowedOrigins.toSet()
    private val allowedMethods = properties.cors.allowedMethods.map(String::uppercase).toSet()
    private val allowedHeaders = properties.cors.allowedHeaders.map { it.lowercase(Locale.ROOT) }.toSet()
    private val exposedHeaders = "X-Trace-Id"
    private val allowedHeaderNames = properties.cors.allowedHeaders
    private val maxAgeSeconds = properties.cors.maxAgeSeconds

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val origin = exchange.request.headers.getFirst(HttpHeaders.ORIGIN) ?: return chain.filter(exchange)
        val requestedMethod = exchange.request.headers.getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)
            ?.uppercase()
        val isPreflight = exchange.request.method == HttpMethod.OPTIONS && requestedMethod != null
        if (origin !in allowedOrigins) {
            return if (isPreflight) {
                responseWriter.write(exchange, HttpStatus.FORBIDDEN, "CORS_FORBIDDEN")
            } else {
                chain.filter(exchange)
            }
        }

        if (isPreflight) {
            val requestedHeaders = exchange.request.headers.getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.map { it.lowercase(Locale.ROOT) }
                .orEmpty()
            if (requestedMethod !in allowedMethods || requestedHeaders.any { it !in allowedHeaders }) {
                return responseWriter.write(exchange, HttpStatus.FORBIDDEN, "CORS_FORBIDDEN")
            }
            exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
            exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
            exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAgeSeconds.toString())
            exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeaders)
            exchange.response.headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN)
            exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods.joinToString(","))
            exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaderNames.joinToString(","))
            exchange.response.headers.add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)
            exchange.response.headers.add(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)
            return exchange.response.setComplete()
        }
        exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
        exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
        exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAgeSeconds.toString())
        exchange.response.headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeaders)
        exchange.response.headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN)
        return chain.filter(exchange)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 5
}
