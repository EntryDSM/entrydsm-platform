package hs.kr.entrydsm.gateway.adapterin.route

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayServiceProperties

import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayServiceProperties::class)
class GatewayRouteConfiguration {

    @Bean
    fun gatewayRouteLocator(
        builder: RouteLocatorBuilder,
        properties: GatewayServiceProperties,
    ): RouteLocator {
        val routes = builder.routes()
        properties.serviceUris.forEach { (service, uri) ->
            routes.route(service.routeId) { route ->
                route.path("${service.pathPrefix}/**")
                    .filters { filters -> filters.stripPrefix(1) }
                    .uri(uri.toString())
            }
        }
        return routes.build()
    }
}
