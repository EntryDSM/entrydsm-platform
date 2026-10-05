package hs.kr.entrydsm.gateway.adapterin.configuration

import hs.kr.entrydsm.gateway.adapterin.route.GatewayRouteConfiguration
import hs.kr.entrydsm.gateway.domain.GatewayService
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
        val routesById = routes.associateBy { it.id }

        assertEquals(GatewayService.entries.size * 2 + 1, routes.size)
        assertEquals(
            GatewayService.entries.flatMap { listOf(it.routeId, "${it.routeId}-openapi") }.toSet() + "application-documents",
            routesById.keys,
        )
        assertEquals(URI("http://localhost:8081"), routesById.getValue("identity").uri)
        assertEquals(URI("http://localhost:8082"), routesById.getValue("application").uri)
        assertEquals(URI("http://localhost:8082"), routesById.getValue("evaluation").uri)
        assertEquals(URI("http://localhost:8086"), routesById.getValue("configuration").uri)
        assertEquals(URI("http://localhost:8082"), routesById.getValue("schedule").uri)
        assertEquals(URI("http://localhost:8082"), routesById.getValue("application-documents").uri)
        assertEquals(-1, routesById.getValue("application-documents").order)
        assertFalse(routes.any { it.id.isBlank() })
    }

    @Test
    fun routesGeneratedDocumentsBeforeGenericFiles() {
        val routes = routeLocator.routes.collectList().block().orEmpty().sortedBy { it.order }
        fun destination(path: String): URI {
            val request = org.springframework.mock.http.server.reactive.MockServerHttpRequest.get(path).build()
            val exchange = org.springframework.mock.web.server.MockServerWebExchange.from(request)
            return routes.first { reactor.core.publisher.Mono.from(it.predicate.apply(exchange)).block() == true }.uri
        }
        for (path in listOf("/api/document/v11/applications", "/api/document/v11/applications/5",
            "/api/document/v11/admission-tickets/5", "/api/document/v11/registration-documents/latest")) {
            assertEquals(URI("http://localhost:8082"), destination(path))
        }
        for (path in listOf("/api/document/v11/photos", "/api/document/v11/registration-documents",
            "/api/document/v11/files/photo_test")) {
            assertEquals(URI("http://localhost:8086"), destination(path))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(GatewayRuntimeConfiguration::class, GatewayRouteConfiguration::class)
    class TestApplication
}
