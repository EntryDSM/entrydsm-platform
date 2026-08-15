package hs.kr.entrydsm.gateway.adapterin.configuration

import hs.kr.entrydsm.gateway.adapterin.route.GatewayRouteConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.net.URI

@SpringBootTest(classes = [GatewayServicePropertiesTest.TestApplication::class], webEnvironment = WebEnvironment.MOCK)
class GatewayServicePropertiesTest {
    @Autowired
    private lateinit var routeLocator: RouteLocator

    @Test
    fun rejectsNonHttpUriWithPathOrQuery() {
        assertThrows(IllegalArgumentException::class.java) {
            GatewayServiceProperties(identity = URI("grpc://identity:9090"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GatewayServiceProperties(identity = URI("http://identity:8080/base?x=1"))
        }
    }

    @Test
    fun registersRoutesForEveryService() {
        val routes = routeLocator.routes.collectList().block().orEmpty()

        assertEquals(6, routes.size)
        assertEquals(URI("http://localhost:8081"), routes.first { it.id == "identity" }.uri)
        assertEquals(URI("http://localhost:8086"), routes.first { it.id == "configuration" }.uri)
        assertFalse(routes.any { it.id.isBlank() })
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GatewayRouteConfiguration::class)
    class TestApplication
}
