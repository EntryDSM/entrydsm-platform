package hs.kr.entrydsm.identity

import java.nio.charset.StandardCharsets
import java.time.Clock
import hs.kr.entrydsm.identity.config.ClockConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityBootstrapConfigurationTest {
    @Test
    fun exposesDeploymentConfigurationWithValidatedSchema() {
        val configuration = requireNotNull(
            javaClass.classLoader.getResourceAsStream("application.yaml"),
        ).use { it.readBytes().toString(StandardCharsets.UTF_8) }

        assertTrue(configuration.contains("ddl-auto: validate"))
        assertTrue(configuration.contains("port: \${SERVER_PORT}"))
        assertTrue(configuration.contains("open-in-view: false"))
        assertTrue(configuration.contains("\${DB_URL}"))
        assertTrue(configuration.contains("secure: \${COOKIE_SECURE}"))
        assertTrue(configuration.contains("allowed-origins: \${IDENTITY_CORS_ALLOWED_ORIGINS}"))
        assertTrue(configuration.contains("proof-key-previous: \${PASS_PROOF_KEY_PREVIOUS:}"))
    }

    @Test
    fun exposesUtcClockBeanForApplicationComponents() {
        assertEquals(Clock.systemUTC().zone, ClockConfig().clock().zone)
    }

    @Test
    fun exposesIdentityMigrationsFromTheInitialSchema() {
        assertTrue(javaClass.classLoader.getResource("db/migration/V001__create_identity_tables.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/V003__protect_identity_personal_data.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/V004__application_projection_and_outbox.sql") != null)
    }
}
