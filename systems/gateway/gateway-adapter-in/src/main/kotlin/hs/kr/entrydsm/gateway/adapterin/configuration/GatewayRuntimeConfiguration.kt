package hs.kr.entrydsm.gateway.adapterin.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    GatewayRuntimeProperties::class,
    GatewayServiceProperties::class,
    DownstreamClientPolicy::class,
)
class GatewayRuntimeConfiguration {
    @Bean("gatewayErrorObjectMapper")
    fun gatewayErrorObjectMapper(): ObjectMapper = ObjectMapper().findAndRegisterModules()
}
