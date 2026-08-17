package hs.kr.entrydsm.identity

import java.nio.charset.StandardCharsets
import java.time.Clock
import hs.kr.entrydsm.identity.config.ClockConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityBootstrapConfigurationTest {
    @Test
    fun exposesProductionConfigurationWithValidatedSchema() {
        val configuration = requireNotNull(
            javaClass.classLoader.getResourceAsStream("application-prod.yaml"),
        ).use { it.readBytes().toString(StandardCharsets.UTF_8) }

        assertTrue(configuration.contains("on-profile: prod"))
        assertTrue(configuration.contains("ddl-auto: validate"))
        assertTrue(configuration.contains("open-in-view: false"))
        assertTrue(configuration.contains("\${DB_URL}"))

        val productionConfiguration = requireNotNull(
            javaClass.classLoader.getResourceAsStream("application-prod.yaml"),
        ).use { it.readBytes().toString(StandardCharsets.UTF_8) }
        assertTrue(productionConfiguration.contains("secure: \${COOKIE_SECURE}"))
        assertTrue(productionConfiguration.contains("allowed-origins: \${IDENTITY_CORS_ALLOWED_ORIGINS}"))
    }

    @Test
    fun exposesUtcClockBeanForApplicationComponents() {
        assertEquals(Clock.systemUTC().zone, ClockConfig().clock().zone)
    }

    @Test
    fun restoresIdentityMigrationsForTheProtectedSchema() {
        assertTrue(javaClass.classLoader.getResource("db/migration/V024__protect_identity_personal_data.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/V025__application_projection_and_outbox.sql") != null)
    }
}
