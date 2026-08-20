package hs.kr.entrydsm.gateway.adapterin.error

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
class DownstreamFailureGlobalFilter(
    private val exceptionHandler: GatewayGlobalExceptionHandler,
) : GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> =
        chain.filter(exchange).onErrorResume { error -> exceptionHandler.handle(exchange, error) }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
}
