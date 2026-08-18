package hs.kr.entrydsm.gateway.adapterin.error

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.gateway.domain.TraceId
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class GatewayErrorResponseWriter(
    @Qualifier("gatewayErrorObjectMapper")
    private val objectMapper: ObjectMapper,
) {
    fun write(
        exchange: ServerWebExchange,
        status: HttpStatusCode,
        error: String,
    ): Mono<Void> {
        exchange.response.statusCode = status
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.cacheControl = "no-store"
        val traceId = exchange.response.headers.getFirst(TraceId.HEADER_NAME)
            ?.let { value -> runCatching { TraceId.from(value).value }.getOrNull() }
        if (traceId == null) {
            exchange.response.headers.remove(TraceId.HEADER_NAME)
        } else {
            exchange.response.headers.set(TraceId.HEADER_NAME, traceId)
        }
        val body = objectMapper.writeValueAsBytes(
            mapOf(
                "status" to status.value(),
                "error" to error,
                "traceId" to (traceId ?: ""),
            ),
        )
        val buffer = exchange.response.bufferFactory().wrap(body)
        return exchange.response.writeWith(Mono.just(buffer))
    }
}
