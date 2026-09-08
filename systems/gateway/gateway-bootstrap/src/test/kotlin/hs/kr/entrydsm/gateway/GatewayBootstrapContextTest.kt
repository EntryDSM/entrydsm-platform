package hs.kr.entrydsm.gateway

import hs.kr.entrydsm.gateway.domain.GatewayService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev")
@SpringBootTest(
    classes = [GatewayBootstrapApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "gateway.resilience.state-store=memory",
        "server.port=0",
    ],
)
class GatewayBootstrapContextTest {
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
}
