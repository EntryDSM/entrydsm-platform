package hs.kr.entrydsm.gateway

import hs.kr.entrydsm.gateway.domain.GatewayService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@ActiveProfiles("dev")
@SpringBootTest(
    classes = [GatewayBootstrapApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "gateway.resilience.state-store=memory",
        "server.port=0",
        "spring.cloud.gateway.server.webflux.globalcors.cors-configurations.[/**].allowedOrigins=" +
            "https://stag-auth.entrydsm.hs.kr",
    ],
)
class GatewayBootstrapContextTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var routeLocator: RouteLocator

    @Test
    fun startsGatewayContextWithRoutes() {
        val routes = routeLocator.routes.collectList().block().orEmpty()
        val routeIds = routes.map { route -> route.id }

        assertTrue(routes.isNotEmpty())
        assertEquals(GatewayService.entries.size, routes.size)
        assertEquals(GatewayService.entries.map { service -> service.routeId }.toSet(), routeIds.toSet())
        assertEquals(routeIds.size, routeIds.toSet().size)
    }

    @Test
    fun handlesCorsPreflightBeforeRouteMatching() {
        WebTestClient.bindToApplicationContext(applicationContext)
            .configureClient()
            .baseUrl("http://gateway.local")
            .build()
            .options()
            .uri("/api/identity/v11/auth/login")
            .header("Origin", "https://stag-auth.entrydsm.hs.kr")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type,x-xsrf-token")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://stag-auth.entrydsm.hs.kr")
            .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
    }
}
