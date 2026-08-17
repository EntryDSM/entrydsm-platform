package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.adapterout.config.JpaAuditingConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.context.annotation.Profile

class PersistenceProfileContractTest {
    @Test
    fun persistenceBeansUseTheSameRuntimeProfiles() {
        val expected = setOf("prod", "dev", "integration")

        assertEquals(expected, profileOf(AccountCommandPersistenceAdapter::class.java))
        assertEquals(expected, profileOf(AccountQueryPersistenceAdapter::class.java))
        assertEquals(expected, profileOf(JpaAuditingConfig::class.java))
    }

    private fun profileOf(type: Class<*>): Set<String> =
        requireNotNull(type.getAnnotation(Profile::class.java)).value.toSet()
}
