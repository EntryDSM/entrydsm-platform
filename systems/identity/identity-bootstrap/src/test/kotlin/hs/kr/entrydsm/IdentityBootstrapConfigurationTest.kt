package hs.kr.entrydsm.identity

import java.nio.charset.StandardCharsets
import java.time.Clock
import hs.kr.entrydsm.identity.config.ClockConfig
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IdentityBootstrapConfigurationTest {
    @Test
    fun exposesProductionConfigurationWithValidatedSchema() {
        val configuration = requireNotNull(
            javaClass.classLoader.getResourceAsStream("application-prod.yaml"),
        ).use { it.readBytes().toString(StandardCharsets.UTF_8) }

        assertTrue(configuration.contains("on-profile: prod"))
        assertTrue(configuration.contains("ddl-auto: validate"))
        assertTrue(configuration.contains("\${DB_URL}"))
        assertTrue(configuration.contains("required-version: \${IDENTITY_SCHEMA_REQUIRED_VERSION:V025}"))
        assertNotNull(javaClass.classLoader.getResource("db/migration/V024__protect_identity_personal_data.sql"))
        assertNotNull(javaClass.classLoader.getResource("db/migration/V025__application_projection_and_outbox.sql"))
    }

    @Test
    fun exposesUtcClockBeanForApplicationComponents() {
        assertEquals(Clock.systemUTC().zone, ClockConfig().clock().zone)
    }
}
