package hs.kr.entrydsm.gateway.adapterin.configuration

import hs.kr.entrydsm.gateway.adapterin.web.CsrfController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.config.EnableWebFlux

@SpringJUnitConfig(SecurityWebConfigTest.TestConfig::class)
class SecurityWebConfigTest {

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp(context: ApplicationContext) {
        client = WebTestClient
            .bindToApplicationContext(context)
            .apply(springSecurity())
            .build()
    }

    @Test
    fun `GET 요청은 CSRF 토큰 없이 허용한다`() {
        client.get()
            .uri("/test")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `POST 요청은 CSRF 토큰이 없으면 거부한다`() {
        client.post()
            .uri("/test")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `CSRF API는 토큰과 쿠키를 발급한다`() {
        val issued = issueCsrfToken()

        assertEquals(issued.token, issued.cookie.value)
        assertTrue(issued.cookie.isHttpOnly)
        assertFalse(issued.cookie.isSecure)
        assertEquals("Lax", issued.cookie.sameSite)
        assertEquals("/", issued.cookie.path)
    }

    @Test
    fun `POST 요청은 발급된 CSRF 쿠키와 헤더가 모두 있으면 허용한다`() {
        val issued = issueCsrfToken()

        client.post()
            .uri("/test")
            .cookie(CSRF_COOKIE, issued.cookie.value)
            .header(CSRF_HEADER, issued.token)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `POST 요청은 CSRF 쿠키만 있으면 거부한다`() {
        val issued = issueCsrfToken()

        client.post()
            .uri("/test")
            .cookie(CSRF_COOKIE, issued.cookie.value)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `POST 요청은 CSRF 헤더만 있으면 거부한다`() {
        val issued = issueCsrfToken()

        client.post()
            .uri("/test")
            .header(CSRF_HEADER, issued.token)
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `POST 요청은 CSRF 쿠키와 헤더 값이 다르면 거부한다`() {
        val issued = issueCsrfToken()

        client.post()
            .uri("/test")
            .cookie(CSRF_COOKIE, issued.cookie.value)
            .header(CSRF_HEADER, "invalid-csrf-token")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `PASS_POPUP 요청은 CSRF 토큰 없이 허용한다`() {
        client.post()
            .uri(PASS_POPUP)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `LOGOUT 요청은 CSRF 토큰 없이 허용한다`() {
        client.post()
            .uri(LOGOUT)
            .exchange()
            .expectStatus().isOk
    }

    private fun issueCsrfToken(): IssuedCsrf {
        val result = client.get()
            .uri(CSRF_PATH)
            .exchange()
            .expectStatus().isOk
            .expectBody(CSRF_RESPONSE_TYPE)
            .returnResult()

        val body = requireNotNull(result.responseBody)
        val token = requireNotNull(body.data).token

        val cookie = requireNotNull(
            result.responseCookies.getFirst(CSRF_COOKIE),
        )

        return IssuedCsrf(
            token = token,
            cookie = cookie,
        )
    }

    private data class IssuedCsrf(
        val token: String,
        val cookie: ResponseCookie,
    )

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Import(
        SecurityWebConfig::class,
        CsrfController::class,
    )
    class TestConfig {

        @Bean
        fun secureCookies(): Boolean = false

        @Bean
        fun testController(): TestController = TestController()
    }

    @RestController
    class TestController {

        @GetMapping("/test")
        fun get(): ResponseEntity<Void> =
            ResponseEntity.ok().build()

        @PostMapping("/test")
        fun post(): ResponseEntity<Void> =
            ResponseEntity.ok().build()

        @PostMapping(PASS_POPUP)
        fun passPopup(): ResponseEntity<Void> =
            ResponseEntity.ok().build()
    }

    private companion object {
        const val CSRF_PATH = "/api/identity/v11/auth/csrf"
        const val CSRF_COOKIE = "XSRF-TOKEN"
        const val CSRF_HEADER = "X-XSRF-TOKEN"

        const val PASS_POPUP = "/api/identity/v11/auth/pass/popup"

        val CSRF_RESPONSE_TYPE =
            object : ParameterizedTypeReference<ApiResponse<CsrfTokenResponse>>() {}
    }
}