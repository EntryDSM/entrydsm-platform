package hs.kr.entrydsm.gateway.adapterin.error

import hs.kr.entrydsm.gateway.domain.TraceId
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

object GatewayErrorResponseWriter {
    fun write(
        exchange: ServerWebExchange,
        status: HttpStatusCode,
        error: String,
    ): Mono<Void> {
        exchange.response.statusCode = status
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.cacheControl = "no-store"
        val traceId = exchange.request.headers.getFirst(TraceId.HEADER_NAME)
            ?: exchange.response.headers.getFirst(TraceId.HEADER_NAME)
        traceId?.let { exchange.response.headers.set(TraceId.HEADER_NAME, it) }
        val body = "{\"status\":${status.value()},\"error\":\"$error\",\"traceId\":\"${traceId ?: ""}\"}"
        val buffer = exchange.response.bufferFactory().wrap(body.toByteArray(Charsets.UTF_8))
        return exchange.response.writeWith(Mono.just(buffer))
    }
}
