package hs.kr.entrydsm.gateway.adapterin.configuration

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRuntimeProperties::class, DownstreamClientPolicy::class)
class GatewayRuntimeConfiguration
