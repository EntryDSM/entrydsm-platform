package hs.kr.entrydsm.gateway.adapterin.integration

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeConfiguration
import hs.kr.entrydsm.gateway.adapterin.error.DownstreamFailureGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.error.GatewayGlobalExceptionHandler
import hs.kr.entrydsm.gateway.adapterin.filter.GatewayAccessGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.filter.GatewayCorsGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.filter.RequestSizeGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.resilience.GatewayCircuitBreakerGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.resilience.GatewayResilienceConfiguration
import hs.kr.entrydsm.gateway.adapterin.resilience.InMemoryGatewayCircuitStateStore
import hs.kr.entrydsm.gateway.adapterin.route.GatewayRouteConfiguration
import hs.kr.entrydsm.gateway.adapterin.trace.TraceIdGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.trace.TraceMdcConfiguration

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatusCode
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.time.Duration

@SpringBootTest(
    classes = [GatewayProxyIntegrationTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "gateway.request.max-body-bytes=10",
        "gateway.resilience.state-store=memory",
        "spring.cloud.gateway.server.webflux.globalcors.enabled=false",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms",
    ],
)
class GatewayProxyIntegrationTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    private lateinit var client: WebTestClient

    @BeforeEach
    fun createClient() {
        client = WebTestClient.bindToApplicationContext(applicationContext)
            .build()
    }

    @Test
    fun proxiesAllServicesAndPreservesRequestDetails() {
        val services = listOf("identity", "application", "admin", "notification", "observability", "configuration")

        services.forEach { service ->
            val response = client.get()
                .uri("/api/$service/users?role=admin")
                .header("X-Trace-Id", "integration-trace")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-Trace-Id", "integration-trace")
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

            assertEquals("GET /$service/users?role=admin", response)
        }
    }

    @Test
    fun proxiesPostAndReturnsDownstreamResponse() {
        val response = client.post()
            .uri("/api/identity/users?source=test")
            .header("X-Trace-Id", "post-trace")
            .bodyValue("payload")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Trace-Id", "post-trace")
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals("POST /identity/users?source=test", response)
    }

    @Test
    fun forwardsAuthorizationToEveryDownstreamServiceWithoutGatewayValidation() {
        val services = listOf("identity", "application", "admin", "notification", "observability", "configuration")

        services.forEach { service ->
            client.get()
                .uri("/api/$service/protected")
                .header("Authorization", "Bearer identity-test-token")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-Downstream-Authorization", "Bearer identity-test-token")
        }

        client.get()
            .uri("/api/identity/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Downstream-Authorization", "missing")
    }

    @Test
    fun returnsNotFoundForUnmatchedPath() {
        client.get()
            .uri("/api/unknown/users")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun appliesCorsAndRequestSizePolicies() {
        client.get()
            .uri("/api/identity/users")
            .header("Origin", "http://localhost:3000")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:3000")
            .expectHeader().valueEquals("Access-Control-Expose-Headers", "X-Trace-Id")

        client.post()
            .uri("/api/identity/users")
            .bodyValue("12345678901")
            .exchange()
            .expectStatus().value { status -> assertEquals(413, status) }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun opensCircuitAfterRepeatedDownstreamFailures() {
        repeat(5) {
            client.get()
                .uri("/api/identity/failure")
                .exchange()
                .expectStatus().isEqualTo(500)
        }

        client.get()
            .uri("/api/identity/failure")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").isEqualTo("CIRCUIT_OPEN")
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun mapsDownstreamTimeoutTo504WithTraceId() {
        client.get()
            .uri("/api/identity/timeout")
            .header("X-Trace-Id", "timeout-integration")
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectHeader().valueEquals("X-Trace-Id", "timeout-integration")
            .expectBody()
            .jsonPath("$.error").isEqualTo("GATEWAY_TIMEOUT")
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(
        GatewayRouteConfiguration::class,
        GatewayRuntimeConfiguration::class,
        GatewayCorsGlobalFilter::class,
        TraceMdcConfiguration::class,
        TraceIdGlobalFilter::class,
        GatewayResilienceConfiguration::class,
        InMemoryGatewayCircuitStateStore::class,
        GatewayAccessGlobalFilter::class,
        RequestSizeGlobalFilter::class,
        GatewayGlobalExceptionHandler::class,
        DownstreamFailureGlobalFilter::class,
        GatewayCircuitBreakerGlobalFilter::class,
    )
    class TestApplication

    companion object {
        private lateinit var downstream: DisposableServer

        @JvmStatic
        @DynamicPropertySource
        fun startDownstream(registry: DynamicPropertyRegistry) {
            downstream = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle { request, response ->
                    response.status(if (request.uri().contains("/failure")) 500 else 200)
                        .addHeader("Content-Type", "text/plain")
                        .addHeader(
                            "X-Downstream-Authorization",
                            request.requestHeaders().get("Authorization") ?: "missing",
                        )
                        .sendString(
                            if (request.uri().contains("/timeout")) {
                                Mono.delay(Duration.ofSeconds(1))
                                    .thenReturn("${request.method().name()} ${request.uri()}")
                            } else {
                                Mono.just("${request.method().name()} ${request.uri()}")
                            },
                        )
                }
                .bindNow()
            val uri = "http://127.0.0.1:${downstream.port()}"
            listOf("identity", "application", "admin", "notification", "observability", "configuration")
                .forEach { service -> registry.add("gateway.services.$service") { uri } }
        }

        @JvmStatic
        @AfterAll
        fun stopDownstream() {
            downstream.disposeNow()
        }
    }
}
