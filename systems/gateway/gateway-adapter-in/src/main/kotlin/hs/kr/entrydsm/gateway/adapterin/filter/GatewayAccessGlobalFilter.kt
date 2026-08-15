package hs.kr.entrydsm.gateway.adapterin.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Keeps the gateway authentication-neutral: identity validates Authorization and applies policy.
 * This filter is intentionally not a Spring bean; it documents the delegation boundary without
 * creating a second authentication decision point.
 */
class GatewayAccessGlobalFilter : GlobalFilter, Ordered {
    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> = chain.filter(exchange)

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10
}
