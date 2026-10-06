package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.IdentityBootstrapApplication
import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.AuthPort
import hs.kr.entrydsm.identity.application.port.`in`.PassPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.port.`in`.command.LogoutCommand
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.model.Account
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@SpringBootTest(
    classes = [IdentityBootstrapApplication::class],
    properties = [
        "auth.jwt.secret=01234567890123456789012345678901",
        "auth.jwt.issuer=entrydsm-identity",
        "security.pii.login-id-hash-key=test-login-id-hash-key",
        "security.pii.encryption-key-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        "pass.proof-key-current=test-pass-proof-key",
        "spring.main.lazy-initialization=true",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    ],
)
class SecurityConfigTest {
    @Autowired private lateinit var webApplicationContext: WebApplicationContext
    @Autowired private lateinit var filterChainProxy: FilterChainProxy
    @MockitoBean private lateinit var accountQueryPort: AccountQueryPort
    @MockitoBean private lateinit var revocationStore: RefreshTokenRevocationStore
    @MockitoBean private lateinit var accountPort: AccountPort
    @MockitoBean private lateinit var applicationPort: ApplicationPort
    @MockitoBean private lateinit var authPort: AuthPort
    @MockitoBean private lateinit var passPort: PassPort
    private lateinit var mockMvc: MockMvc

    @Before
    fun setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters<DefaultMockMvcBuilder>(filterChainProxy)
            .build()
    }

    @Test
    fun protectedEndpointsRejectUnauthenticatedRequests() {
        listOf(
            get("/api/identity/v11/accounts/me"),
            delete("/api/identity/v11/accounts/me"),
            get("/api/identity/v11/applications/status"),
            get("/api/identity/v11/applications/result"),
        ).forEach { request ->
            val response = mockMvc.perform(request).andReturn().response
            assertEquals(401, response.status)
            assertTrue(response.contentAsString.contains("AUTH_UNAUTHORIZED"))
        }
    }

    @Test
    fun publicEndpointsDoNotRequireAuthenticationOrCsrf() {
        assertEquals(200, mockMvc.perform(get("/actuator/health")).andReturn().response.status)
        assertEquals(200, mockMvc.perform(post("/api/identity/v11/auth/logout")).andReturn().response.status)
        assertEquals(
            400,
            mockMvc.perform(
                post("/api/identity/v11/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andReturn().response.status,
        )
    }

    @Test
    fun logoutWithExpiredInvalidOrMissingTokenDeletesBothCookiesWithoutRevocation() {
        val expired = JwtTokenGenerator(
            "01234567890123456789012345678901", "entrydsm-identity",
            Clock.fixed(Instant.now().minusSeconds(7201), ZoneOffset.UTC),
        ).generateAccessToken("user_123").value
        listOf(expired, "invalid-token", null).forEach { token ->
            val request = post("/api/identity/v11/auth/logout")
            if (token != null) request.cookie(Cookie("access_token", token))
            val response = mockMvc.perform(request).andReturn().response
            assertEquals(200, response.status)
            listOf("access_token", "refresh_token").forEach { name ->
                assertTrue(response.getHeaders("Set-Cookie").any {
                    it.startsWith("$name=") && it.contains("Max-Age=0") && it.contains("Path=/")
                })
            }
        }
        verifyNoInteractions(authPort)
    }

    @Test
    fun validLogoutKeepsAuthenticatedRevocationAndExpiresCookies() {
        val account = mock(Account::class.java)
        `when`(account.status).thenReturn(AccountStatus.ACTIVE)
        `when`(accountQueryPort.findByUserId(123L)).thenReturn(account)
        val token = JwtTokenGenerator("01234567890123456789012345678901", "entrydsm-identity")
            .generateAccessToken("user_123").value
        val response = mockMvc.perform(post("/api/identity/v11/auth/logout")
            .cookie(Cookie("access_token", token))).andReturn().response
        assertEquals(200, response.status)
        verify(authPort).logout(LogoutCommand(123L))
        assertEquals(2, response.getHeaders("Set-Cookie").count { it.contains("Max-Age=0") })
    }

    @Test
    fun invalidTokenRemainsRejectedOutsidePostLogout() {
        listOf(get("/api/identity/v11/accounts/me"), get("/api/identity/v11/auth/logout"))
            .forEach { request ->
                val response = mockMvc.perform(request.cookie(Cookie("access_token", "invalid-token")))
                    .andReturn().response
                assertEquals(401, response.status)
            }
        verifyNoInteractions(authPort)
    }

    @Test
    fun identityDoesNotEmitCorsHeaders() {
        val response = mockMvc.perform(get("/actuator/health").header("Origin", "https://frontend.example"))
            .andReturn().response
        assertEquals(null, response.getHeader("Access-Control-Allow-Origin"))
    }

    @Test
    fun productionSecurityRejectsInsecureCookies() {
        assertThrows(IllegalArgumentException::class.java) {
            SecurityConfigurationValidator.validate(secureCookies = false, production = true)
        }
    }
}
