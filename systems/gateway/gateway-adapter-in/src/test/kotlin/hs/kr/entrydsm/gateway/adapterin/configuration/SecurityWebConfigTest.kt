package hs.kr.entrydsm.gateway.adapterin.configuration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.ResponseEntity
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.reactive.server.MockServerConfigurer
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.config.EnableWebFlux

@SpringJUnitConfig(SecurityWebConfigTest.TestConfig::class)
class SecurityWebConfigTest {

    private lateinit var client: WebTestClient

    @BeforeEach
    fun setUp(context: ApplicationContext, springSecurity: () -> MockServerConfigurer) {
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
    fun `POST 요청은 유효한 CSRF 토큰이 있으면 허용한다`() {
        client
            .mutateWith(csrf())
            .post()
            .uri("/test")
            .exchange()
            .expectStatus().isOk
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

    @Configuration(proxyBeanMethods = false)
    @EnableWebFlux
    @Import(SecurityWebConfig::class)
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

        @PostMapping(LOGOUT)
        fun logout(): ResponseEntity<Void> =
            ResponseEntity.ok().build()
    }

    private companion object {
        const val PASS_POPUP = "/api/identity/v11/auth/pass/popup"
        const val LOGOUT = "/api/identity/v11/auth/logout"
    }
}