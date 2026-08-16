package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.IdentityBootstrapApplication
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockCookie
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.context.WebApplicationContext
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.security.web.FilterChainProxy

@RunWith(SpringRunner::class)
@ActiveProfiles("test")
@SpringBootTest(
    classes = [IdentityBootstrapApplication::class],
    properties = [
        "auth.jwt.secret=01234567890123456789012345678901",
        "auth.jwt.issuer=entrydsm-identity",
        "spring.main.lazy-initialization=true",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    ],
)
class SecurityConfigTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext
    @Autowired
    private lateinit var filterChainProxy: FilterChainProxy
    private lateinit var mockMvc: MockMvc

    @Before
    fun setUpMockMvc() {
        val builder: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(filterChainProxy)
        mockMvc = builder.build()
    }
    @Test
    fun protectedStateChangingRequestRequiresCsrfHeader() {
        val response = mockMvc.perform(
            post("/api/identity/v11/accounts/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andReturn().response

        assertEquals(403, response.status)
        assertNotNull(response.getCookie("XSRF-TOKEN"))
    }

    @Test
    fun csrfCookieIsIssuedAndMatchingHeaderPassesCsrfValidation() {
        val csrfResponse = mockMvc.perform(
            get("/api/identity/v11/accounts/me"),
        ).andReturn().response
        val csrfCookie = requireNotNull(csrfResponse.getCookie("XSRF-TOKEN"))
        val csrfToken = java.net.URLDecoder.decode(csrfCookie.value, StandardCharsets.UTF_8)

        val protectedResponse = mockMvc.perform(
            post("/api/identity/v11/accounts/me")
                .cookie(MockCookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andReturn().response

        // CSRF passed; the request then reaches authentication and is rejected as unauthenticated.
        assertEquals(401, protectedResponse.status)
        assertTrue(protectedResponse.getErrorMessage()?.contains("Full authentication") != true)
    }

    @Test
    fun publicAuthRequestWithoutCsrfHeaderIsRejected() {
        val response = mockMvc.perform(
            post("/api/identity/v11/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andReturn().response

        assertEquals(403, response.status)
    }

    @Test
    fun publicAuthRequestRemainsPublicAfterCsrfValidation() {
        val csrfResponse = mockMvc.perform(
            get("/actuator/health"),
        ).andReturn().response
        val csrfCookie = requireNotNull(csrfResponse.getCookie("XSRF-TOKEN"))
        val csrfToken = java.net.URLDecoder.decode(csrfCookie.value, StandardCharsets.UTF_8)

        val response = mockMvc.perform(
            post("/api/identity/v11/auth/login")
                .cookie(MockCookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andReturn().response

        assertTrue(response.status != 401)
        assertTrue(response.status != 403)
    }

    @Test
    fun deleteAccountWithoutAuthorizationReturnsUnauthorizedError() {
        val csrfToken = csrfToken()

        val response = mockMvc.perform(
            delete("/api/identity/v11/accounts/me")
                .cookie(MockCookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken),
        ).andReturn().response

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun applicationStatusWithoutAuthorizationReturnsUnauthorizedError() {
        val response = mockMvc.perform(
            get("/api/identity/v11/applications/status"),
        ).andReturn().response

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun applicationResultWithoutAuthorizationReturnsUnauthorizedError() {
        val response = mockMvc.perform(
            get("/api/identity/v11/applications/result"),
        ).andReturn().response

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun applicationCancellationWithoutAuthorizationReturnsUnauthorizedError() {
        val csrfToken = csrfToken()

        val response = mockMvc.perform(
            patch("/api/identity/v11/applications/cancellation")
                .cookie(MockCookie("XSRF-TOKEN", csrfToken))
                .header("X-XSRF-TOKEN", csrfToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        ).andReturn().response

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("AUTH_UNAUTHORIZED"))
    }

    private fun csrfToken(): String {
        val csrfResponse = mockMvc.perform(
            get("/actuator/health"),
        ).andReturn().response
        val csrfCookie = requireNotNull(csrfResponse.getCookie("XSRF-TOKEN"))
        return java.net.URLDecoder.decode(csrfCookie.value, StandardCharsets.UTF_8)
    }
}
