package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import hs.kr.entrydsm.gateway.domain.TraceId
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.util.concurrent.atomic.AtomicLong

@Component
class RequestSizeGlobalFilter(
    properties: GatewayRuntimeProperties,
) : GlobalFilter, Ordered {
    private val maxBodyBytes = properties.request.maxBodyBytes

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val contentLength = exchange.request.headers.contentLength
        if (contentLength > maxBodyBytes) {
            return GatewayErrorResponseWriter.write(exchange, HttpStatusCode.valueOf(413), "REQUEST_TOO_LARGE")
        }

        val bytesRead = AtomicLong(0)
        val request = object : ServerHttpRequestDecorator(exchange.request) {
            override fun getBody(): Flux<DataBuffer> = super.getBody().handle { buffer, sink ->
                val total = bytesRead.addAndGet(buffer.readableByteCount().toLong())
                if (total > maxBodyBytes) {
                    DataBufferUtils.release(buffer)
                    sink.error(GatewayRequestTooLargeException())
                } else {
                    sink.next(buffer)
                }
            }
        }
        return chain.filter(exchange.mutate().request(request).build())
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 20
}
