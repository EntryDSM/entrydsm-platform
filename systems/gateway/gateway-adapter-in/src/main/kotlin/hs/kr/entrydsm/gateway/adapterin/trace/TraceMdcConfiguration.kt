package hs.kr.entrydsm.gateway.adapterin.trace

import io.micrometer.context.ContextRegistry
import hs.kr.entrydsm.gateway.domain.TraceId
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.MDC
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration(proxyBeanMethods = false)
class TraceMdcConfiguration {
    @PostConstruct
    fun register() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
            TraceContextKeys.KEY,
            { MDC.get(TraceId.HEADER_NAME) },
            { traceId: String? ->
                if (traceId == null) {
                    MDC.remove(TraceId.HEADER_NAME)
                } else {
                    MDC.put(TraceId.HEADER_NAME, traceId)
                }
            },
            { MDC.remove(TraceId.HEADER_NAME) },
        )
        Hooks.enableAutomaticContextPropagation()
    }

    @PreDestroy
    fun unregister() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(TraceContextKeys.KEY)
        Hooks.disableAutomaticContextPropagation()
    }
}
