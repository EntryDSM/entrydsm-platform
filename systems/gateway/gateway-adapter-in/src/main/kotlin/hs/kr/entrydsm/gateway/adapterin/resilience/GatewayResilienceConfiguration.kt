package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class GatewayResilienceConfiguration {
    @Bean
    fun gatewayCircuitBreakerRegistry(
        properties: GatewayRuntimeProperties,
    ): CircuitBreakerRegistry {
        val resilience = properties.resilience
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(resilience.failureRateThreshold.toFloat())
            .slidingWindowSize(resilience.slidingWindowSize)
            .minimumNumberOfCalls(resilience.minimumNumberOfCalls)
            .waitDurationInOpenState(Duration.ofSeconds(resilience.waitDurationSeconds))
            .permittedNumberOfCallsInHalfOpenState(resilience.permittedNumberOfCallsInHalfOpenState)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build()
        return CircuitBreakerRegistry.of(config)
    }
}
