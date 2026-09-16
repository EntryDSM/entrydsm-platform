package hs.kr.entrydsm.identity

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityBootstrapConfigurationTest {
    @Test
    fun exposesModuleConfigurationWithoutPlatformSettings() {
        val configuration = requireNotNull(
            javaClass.classLoader.getResourceAsStream("modules/identity.yaml"),
        ).use { it.readBytes().toString(StandardCharsets.UTF_8) }

        assertTrue(configuration.contains("secure: \${COOKIE_SECURE}"))
        assertFalse(configuration.contains("IDENTITY_CORS_ALLOWED_ORIGINS"))
        assertTrue(configuration.contains("proof-key-previous: \${PASS_PROOF_KEY_PREVIOUS:}"))
        // DB·Redis 연결과 서버 포트는 platform 이 정한다. 모듈 설정에 두면 다른 모듈 설정과 겹친다.
        assertFalse(configuration.contains("datasource"))
        assertFalse(configuration.contains("server:"))
        assertFalse(configuration.contains("\${REDIS_URL}"))
    }

    @Test
    fun doesNotShipRootApplicationConfiguration() {
        assertTrue(javaClass.classLoader.getResource("application.yaml") == null)
    }

    @Test
    fun exposesIdentityMigrationsFromTheInitialSchema() {
        assertTrue(javaClass.classLoader.getResource("db/migration/identity/V001__create_identity_tables.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/identity/V002__student_profiles_birthdate_date.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/identity/V003__protect_identity_personal_data.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/identity/V004__application_projection_and_outbox.sql") != null)
        assertTrue(javaClass.classLoader.getResource("db/migration/identity/V005__add_sensitive_consent.sql") != null)
    }
}
