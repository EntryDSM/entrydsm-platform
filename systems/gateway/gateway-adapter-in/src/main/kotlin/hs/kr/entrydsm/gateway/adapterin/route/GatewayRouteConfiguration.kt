package hs.kr.entrydsm.gateway.adapterin.route

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayServiceProperties

import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class GatewayRouteConfiguration {

    @Bean
    fun gatewayRouteLocator(
        builder: RouteLocatorBuilder,
        properties: GatewayServiceProperties,
    ): RouteLocator {
        val routes = builder.routes()
        routes.route("application-documents") { route ->
            route.order(-1).path(
                "/api/document/v11/applications", "/api/document/v11/applications/**",
                "/api/document/v11/admission-tickets/**", "/api/document/v11/registration-documents/latest",
            ).uri(properties.application.toString())
        }
        properties.serviceUris.forEach { (service, uri) ->
            routes.route(service.routeId) { route ->
                route.path("${service.pathPrefix}/**")
                    .filters { filters ->
                        // Keep the gateway's /api prefix in the downstream service path.
                        // stripPrefix remains part of the route normalization pipeline.
                        filters
                            .stripPrefix(1)
                            .prefixPath("/api")
                    }
                    .uri(uri.toString())
            }

            routes.route("${service.routeId}-openapi") { route ->
                route.path("/swagger/${service.routeId}/**")
                    .filters { filters ->
                        filters.rewritePath(
                            "/swagger/${service.routeId}/(?<remaining>.*)",
                            "/\${remaining}",
                        )
                    }
                    .uri(uri.toString())
            }
        }
        return routes.build()
    }
}
