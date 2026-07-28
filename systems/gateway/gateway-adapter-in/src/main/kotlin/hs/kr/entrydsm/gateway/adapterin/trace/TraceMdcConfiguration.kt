package hs.kr.entrydsm.gateway.adapterin.trace

import hs.kr.entrydsm.gateway.domain.TraceId
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.MDC
import org.springframework.context.annotation.Configuration
import reactor.core.CoreSubscriber
import reactor.core.publisher.Hooks
import reactor.core.publisher.Operators
import reactor.util.context.Context
import org.reactivestreams.Subscription

@Configuration(proxyBeanMethods = false)
class TraceMdcConfiguration {
    @PostConstruct
    fun register() {
        Hooks.onEachOperator(HOOK_KEY, Operators.lift { _, subscriber -> TraceMdcSubscriber(subscriber) })
    }

    @PreDestroy
    fun unregister() {
        Hooks.resetOnEachOperator(HOOK_KEY)
    }

    private class TraceMdcSubscriber<T : Any>(
        private val delegate: CoreSubscriber<in T>,
    ) : CoreSubscriber<T> {
        override fun currentContext(): Context = delegate.currentContext()

        override fun onSubscribe(subscription: Subscription) {
            delegate.onSubscribe(subscription)
        }

        override fun onNext(value: T) {
            withTraceId { delegate.onNext(value) }
        }

        override fun onError(throwable: Throwable) {
            withTraceId { delegate.onError(throwable) }
        }

        override fun onComplete() {
            withTraceId { delegate.onComplete() }
        }

        private fun withTraceId(block: () -> Unit) {
            val traceId = currentContext().getOrEmpty<String>(TRACE_CONTEXT_KEY).orElse(null)
            val previous = MDC.get(TraceId.HEADER_NAME)
            if (traceId != null) {
                MDC.put(TraceId.HEADER_NAME, traceId)
            }
            try {
                block()
            } finally {
                if (previous == null) {
                    MDC.remove(TraceId.HEADER_NAME)
                } else {
                    MDC.put(TraceId.HEADER_NAME, previous)
                }
            }
        }
    }

    private companion object {
        const val HOOK_KEY = "gateway-trace-mdc"
        const val TRACE_CONTEXT_KEY = "gateway.trace-id"
    }
}
