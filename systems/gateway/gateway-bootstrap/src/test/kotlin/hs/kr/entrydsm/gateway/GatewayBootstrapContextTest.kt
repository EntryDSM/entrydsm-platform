package hs.kr.entrydsm.gateway

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.gateway.route.RouteLocator

@SpringBootTest(
    classes = [GatewayBootstrapApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
class GatewayBootstrapContextTest {
    @Autowired
    private lateinit var routeLocator: RouteLocator

    @Test
    fun startsGatewayContextWithRoutes() {
        val routes = routeLocator.routes.collectList().block().orEmpty()
        assertTrue(routes.isNotEmpty())
    }
}
