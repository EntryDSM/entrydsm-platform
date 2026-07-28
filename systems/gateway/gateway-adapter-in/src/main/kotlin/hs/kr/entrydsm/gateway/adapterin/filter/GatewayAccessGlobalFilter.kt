package hs.kr.entrydsm.gateway.adapterin.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

class GatewayAccessGlobalFilter : GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> = chain.filter(exchange)

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10
}
