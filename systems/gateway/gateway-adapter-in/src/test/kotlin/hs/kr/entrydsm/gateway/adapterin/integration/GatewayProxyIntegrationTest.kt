package hs.kr.entrydsm.gateway.adapterin.integration

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeConfiguration
import hs.kr.entrydsm.gateway.adapterin.error.DownstreamFailureGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.error.GatewayGlobalExceptionHandler
import hs.kr.entrydsm.gateway.adapterin.error.GatewayErrorResponseWriter
import hs.kr.entrydsm.gateway.adapterin.filter.GatewayAccessGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.filter.RequestSizeGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.resilience.GatewayCircuitBreakerGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.resilience.GatewayResilienceConfiguration
import hs.kr.entrydsm.gateway.adapterin.resilience.InMemoryGatewayCircuitStateStore
import hs.kr.entrydsm.gateway.adapterin.route.GatewayRouteConfiguration
import hs.kr.entrydsm.gateway.adapterin.trace.TraceIdGlobalFilter
import hs.kr.entrydsm.gateway.adapterin.trace.TraceMdcConfiguration
import hs.kr.entrydsm.gateway.domain.GatewayService

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
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import reactor.netty.resources.LoopResources
import java.time.Duration

@SpringBootTest(
    classes = [GatewayProxyIntegrationTest.TestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "gateway.request.max-body-bytes=10",
        "gateway.resilience.state-store=memory",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=2s",
    ],
)
class GatewayProxyIntegrationTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var stateStore: InMemoryGatewayCircuitStateStore

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    private lateinit var client: WebTestClient

    @BeforeEach
    fun createClient() {
        stateStore.clear()
        circuitBreakerRegistry.allCircuitBreakers.forEach { it.transitionToClosedState() }
        client = WebTestClient.bindToApplicationContext(applicationContext)
            .build()
    }

    @Test
    fun proxiesAllServicesAndPreservesRequestDetails() {
        GatewayService.entries.forEach { service ->
            val requestUri = "${service.pathPrefix}/users?role=admin"
            val response = client.get()
                .uri(requestUri)
                .header("X-Trace-Id", "integration-trace")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-Trace-Id", "integration-trace")
                .expectBody(String::class.java)
                .returnResult()
                .responseBody

            assertEquals("GET $requestUri", response)
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

        assertEquals("POST /api/identity/users?source=test", response)
    }

    @Test
    fun validatesAuthorizationAndInjectsTrustedUserHeaders() {
        GatewayService.entries.forEach { service ->
            client.get()
                .uri("${service.pathPrefix}/protected")
                .header("Authorization", "Bearer identity-test-token")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-Downstream-Authorization", "Bearer identity-test-token")
        }

        client.get()
            .uri("/api/v11/admin/protected")
            .header("Authorization", "Bearer identity-test-token")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Downstream-User-Id", "123")
            .expectHeader().valueEquals("X-Downstream-User-Role", "ADMIN")
            .expectHeader().valueEquals("X-Downstream-Application-User-Id", "123")
            .expectHeader().valueEquals("X-Downstream-Sensitive-Agree", "true")

        client.get()
            .uri("/api/identity/login")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Downstream-Authorization", "missing")
    }

    @Test
    fun stripsClientSuppliedTrustedUserHeaders() {
        client.get()
            .uri("/api/v11/admin/applicants")
            .header("X-User-Id", "attacker")
            .header("X-User-Role", "ADMIN")
            .header("user-id", "999")
            .header("X-Sensitive-Agree", "true")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Downstream-User-Id", "missing")
            .expectHeader().valueEquals("X-Downstream-User-Role", "missing")
            .expectHeader().valueEquals("X-Downstream-Application-User-Id", "missing")
            .expectHeader().valueEquals("X-Downstream-Sensitive-Agree", "missing")
    }

    @Test
    fun rejectsInvalidAccessToken() {
        client.get()
            .uri("/api/v11/admin/applicants")
            .header("Authorization", "Bearer invalid-token")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.error").isEqualTo("AUTH_UNAUTHORIZED")
    }

    @Test
    fun requiresCsrfForCookieAuthenticatedWriteRequest() {
        client.post()
            .uri("/api/application/v11/applicants")
            .cookie("access_token", "identity-test-token")
            .cookie("XSRF-TOKEN", "csrf-token")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.error").isEqualTo("CSRF_INVALID")

        client.post()
            .uri("/api/application/v11/applicants")
            .cookie("access_token", "identity-test-token")
            .cookie("XSRF-TOKEN", "csrf-token")
            .header("X-XSRF-TOKEN", "csrf-token")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-Downstream-User-Id", "123")
    }

    @Test
    fun forwardsCookieHeaderToDownstreamService() {
        client.get()
            .uri("/api/identity/accounts/me")
            .header("Cookie", "access_token=identity-test-token; XSRF-TOKEN=csrf-test-token")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(
                "X-Downstream-Cookie",
                "access_token=identity-test-token; XSRF-TOKEN=csrf-test-token",
            )
    }

    @Test
    fun returnsNotFoundForUnmatchedPath() {
        client.get()
            .uri("/api/unknown/users")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun appliesRequestSizePolicy() {
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
        TraceMdcConfiguration::class,
        TraceIdGlobalFilter::class,
        GatewayResilienceConfiguration::class,
        InMemoryGatewayCircuitStateStore::class,
        GatewayAccessGlobalFilter::class,
        RequestSizeGlobalFilter::class,
        GatewayGlobalExceptionHandler::class,
        GatewayErrorResponseWriter::class,
        DownstreamFailureGlobalFilter::class,
        GatewayCircuitBreakerGlobalFilter::class,
    )
    class TestApplication

    companion object {
        private var downstream: DisposableServer? = null
        private var downstreamLoops: LoopResources? = null

        @JvmStatic
        @DynamicPropertySource
        fun startDownstream(registry: DynamicPropertyRegistry) {
            val server = downstream ?: HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .runOn(LoopResources.create("gateway-review-downstream", 1, true).also { downstreamLoops = it })
                .handle { request, response ->
                    val isAuthorityRequest = request.uri() == "/api/identity/v11/accounts/me/authority"
                    val invalidToken = request.requestHeaders().get("Authorization") == "Bearer invalid-token"
                    val body = if (isAuthorityRequest && !invalidToken) {
                        """{"success":true,"data":{"userId":"user_123","role":"ADMIN","status":"ACTIVE","isSensitiveAgree":true}}"""
                    } else {
                        "${request.method().name()} ${request.uri()}"
                    }
                    val bodyPublisher = if (request.uri().contains("/timeout")) {
                        Mono.just(body).delaySubscription(Duration.ofSeconds(3))
                    } else {
                        Mono.just(body)
                    }
                    val status = when {
                        isAuthorityRequest && invalidToken -> 401
                        request.uri().contains("/failure") -> 500
                        else -> 200
                    }
                    response.status(status)
                        .addHeader("Content-Type", if (isAuthorityRequest) "application/json" else "text/plain")
                        .addHeader("Content-Length", body.toByteArray().size.toString())
                        .addHeader(
                            "X-Downstream-Authorization",
                            request.requestHeaders().get("Authorization") ?: "missing",
                        )
                        .addHeader(
                            "X-Downstream-Cookie",
                            request.requestHeaders().get("Cookie") ?: "missing",
                        )
                        .addHeader(
                            "X-Downstream-User-Id",
                            request.requestHeaders().get("X-User-Id") ?: "missing",
                        )
                        .addHeader(
                            "X-Downstream-User-Role",
                            request.requestHeaders().get("X-User-Role") ?: "missing",
                        )
                        .addHeader(
                            "X-Downstream-Application-User-Id",
                            request.requestHeaders().get("user-id") ?: "missing",
                        )
                        .addHeader(
                            "X-Downstream-Sensitive-Agree",
                            request.requestHeaders().get("X-Sensitive-Agree") ?: "missing",
                        )
                        .sendString(bodyPublisher)
                        .then()
                }
                .bindNow()
                .also { downstream = it }
            val uri = "http://127.0.0.1:${server.port()}"
            GatewayService.entries
                .map { service -> service.downstreamKey }
                .distinct()
                .forEach { downstream -> registry.add("gateway.services.${downstream.propertyKey}") { uri } }
        }

        @JvmStatic
        @AfterAll
        fun stopDownstream() {
            downstream?.disposeNow()
            downstreamLoops?.disposeLater()?.block()
            downstream = null
            downstreamLoops = null
        }
    }
}
