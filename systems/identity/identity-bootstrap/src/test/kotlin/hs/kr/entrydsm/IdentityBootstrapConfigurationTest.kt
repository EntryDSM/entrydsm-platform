package hs.kr.entrydsm.identity

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
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
    }
}
