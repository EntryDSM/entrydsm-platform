package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.IdentityBootstrapApplication
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockCookie
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@RunWith(SpringRunner::class)
@ActiveProfiles("integration")
@SpringBootTest(classes = [IdentityBootstrapApplication::class])
class IdentityApplicationHttpIntegrationTest {
    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var filterChainProxy: FilterChainProxy

    @Autowired
    private lateinit var accountRepository: AccountRepository

    private lateinit var mockMvc: MockMvc

    @Before
    fun setUpMockMvc() {
        val builder: DefaultMockMvcBuilder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(filterChainProxy)
        mockMvc = builder.build()
    }

    @Test
    fun authenticatedApplicationEndpointsUseRealAuthAndAccountPersistence() {
        val signupResponse = mockMvc.perform(
            post("/api/identity/v11/auth/signup")
                .withCsrf()
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"password":"Password1!","name":"홍길동","phone":"$PHONE","birthdate":"2009-03-15","signupType":"SELF"}""",
                ),
        ).andReturn().response
        assertEquals(201, signupResponse.status)

        val account = requireNotNull(accountRepository.findByLoginId(PHONE))
        account.profile.submit(SUBMITTED_AT)
        accountRepository.save(account)

        val loginResponse = mockMvc.perform(
            post("/api/identity/v11/auth/login")
                .withCsrf()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"loginId":"$PHONE","password":"Password1!"}"""),
        ).andReturn().response
        assertEquals(200, loginResponse.status)
        val accessToken = requireNotNull(
            loginResponse.getHeaders(HttpHeaders.SET_COOKIE)
                .firstOrNull { it.startsWith("access_token=") },
        ).substringAfter("access_token=").substringBefore(';')

        val invalidTokenResponse = mockMvc.perform(
            get("/api/identity/v11/applications/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"),
        ).andReturn().response
        assertEquals(401, invalidTokenResponse.status)
        assertTrue(invalidTokenResponse.contentAsString.contains("AUTH_UNAUTHORIZED"))

        val statusResponse = mockMvc.perform(
            get("/api/identity/v11/applications/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        ).andReturn().response
        assertEquals(200, statusResponse.status)
        assertTrue(statusResponse.contentAsString.contains("\"applicantStatus\":\"SUBMITTED\""))

        val unpublishedResultResponse = mockMvc.perform(
            get("/api/identity/v11/applications/result")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        ).andReturn().response
        assertEquals(409, unpublishedResultResponse.status)
        assertTrue(unpublishedResultResponse.contentAsString.contains("APPLICATION_RESULT_NOT_AVAILABLE"))

        val cancellationResponse = mockMvc.perform(
            patch("/api/identity/v11/applications/cancellation")
                .withCsrf()
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"개인 사유"}"""),
        ).andReturn().response
        assertEquals(200, cancellationResponse.status)
        assertTrue(cancellationResponse.contentAsString.contains("\"applicantStatus\":\"CANCELED\""))

        val persistedAccount = requireNotNull(accountRepository.findByUserId(account.userId))
        assertEquals(ApplicantStatus.CANCELED, persistedAccount.profile.applicantStatus)
        assertEquals(SUBMITTED_AT, persistedAccount.profile.submittedAt)

        val canceledStatusResponse = mockMvc.perform(
            get("/api/identity/v11/applications/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        ).andReturn().response
        assertEquals(200, canceledStatusResponse.status)
        assertTrue(canceledStatusResponse.contentAsString.contains("\"applicantStatus\":\"CANCELED\""))
    }

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.withCsrf() =
        csrfToken().let { token ->
            cookie(MockCookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
        }

    private fun csrfToken(): String {
        val response = mockMvc.perform(get("/actuator/health")).andReturn().response
        val cookie = requireNotNull(response.getCookie("XSRF-TOKEN"))
        return URLDecoder.decode(cookie.value, StandardCharsets.UTF_8)
    }

    companion object {
        private const val MYSQL_IMAGE = "mysql:8.4"
        private const val MYSQL_PORT = 3306
        private const val REDIS_IMAGE = "redis:7.4"
        private const val REDIS_PORT = 6379
        private const val DATABASE = "identity_http_test"
        private const val PHONE = "01012345679"
        private val SUBMITTED_AT = Instant.parse("2026-06-11T11:00:00Z")
        private lateinit var mysql: GenericContainer<Nothing>
        private lateinit var redis: GenericContainer<Nothing>
        private var containersStarted = false

        @JvmStatic
        @BeforeClass
        fun checkDocker() {
            val available = DockerClientFactory.instance().isDockerAvailable
            if (!available && integrationTestsAreRequired()) {
                error("Docker daemon is required for HTTP persistence integration tests")
            }
            assumeTrue("Docker daemon is required for HTTP persistence integration tests", available)
        }

        private fun integrationTestsAreRequired(): Boolean =
            System.getenv("IDENTITY_INTEGRATION_REQUIRED").equals("true", ignoreCase = true)

        @JvmStatic
        @DynamicPropertySource
        fun registerContainerProperties(registry: DynamicPropertyRegistry) {
            mysql = GenericContainer<Nothing>(DockerImageName.parse(MYSQL_IMAGE))
                .withExposedPorts(MYSQL_PORT)
            mysql.addEnv("MYSQL_DATABASE", DATABASE)
            mysql.addEnv("MYSQL_USER", "identity")
            mysql.addEnv("MYSQL_PASSWORD", "identity")
            mysql.addEnv("MYSQL_ROOT_PASSWORD", "root")
            mysql.start()

            redis = GenericContainer<Nothing>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
            redis.start()
            containersStarted = true

            registry.add("spring.datasource.url") {
                "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(MYSQL_PORT)}/$DATABASE?useSSL=false&serverTimezone=UTC"
            }
            registry.add("spring.datasource.username") { "identity" }
            registry.add("spring.datasource.password") { "identity" }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.jpa.properties.hibernate.dialect") { "org.hibernate.dialect.MySQLDialect" }
            registry.add("spring.data.redis.url") {
                "redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}"
            }
            registry.add("auth.jwt.secret") { "01234567890123456789012345678901" }
            registry.add("auth.jwt.issuer") { "entrydsm-identity" }
            registry.add("security.cookies.secure") { "false" }
        }

        @JvmStatic
        @AfterClass
        fun stopContainers() {
            if (containersStarted) {
                redis.stop()
                mysql.stop()
            }
        }
    }
}
