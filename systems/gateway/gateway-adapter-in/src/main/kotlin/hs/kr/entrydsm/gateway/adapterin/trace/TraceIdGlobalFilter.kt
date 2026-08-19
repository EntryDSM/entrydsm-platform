package hs.kr.entrydsm.gateway.adapterin.trace

import hs.kr.entrydsm.gateway.adapterin.error.InvalidTraceIdException
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import hs.kr.entrydsm.gateway.domain.TraceId
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class TraceIdGlobalFilter : GlobalFilter, Ordered {

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val incomingTraceId = exchange.request.headers.getFirst(TraceId.HEADER_NAME)
        val traceId = try {
            incomingTraceId?.let(TraceId::from) ?: TraceId.generated(UUID.randomUUID()::toString)
        } catch (exception: IllegalArgumentException) {
            return Mono.error(InvalidTraceIdException(exception))
        }

        val request = exchange.request.mutate()
            .headers { headers -> headers.set(TraceId.HEADER_NAME, traceId.value) }
            .build()

        exchange.response.headers.set(TraceId.HEADER_NAME, traceId.value)
        exchange.response.beforeCommit {
            exchange.response.headers.set(TraceId.HEADER_NAME, traceId.value)
            Mono.empty()
        }
        return chain.filter(exchange.mutate().request(request).build())
            .contextWrite { context -> context.put(TraceContextKeys.KEY, traceId.value) }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

}
