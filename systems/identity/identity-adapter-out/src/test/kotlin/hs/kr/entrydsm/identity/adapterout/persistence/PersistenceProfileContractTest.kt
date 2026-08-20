package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.adapterout.config.JpaAuditingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import org.mockito.Mockito.mock

class PersistenceProfileContractTest {
    @Test
    fun persistenceBeansUseTheSameRuntimeProfiles() {
        val expected = setOf("prod", "dev", "integration")

        assertEquals(expected, profileOf(AccountCommandPersistenceAdapter::class.java))
        assertEquals(expected, profileOf(AccountQueryPersistenceAdapter::class.java))
        assertEquals(expected, profileOf(JpaAuditingConfig::class.java))
    }

    @Test
    fun commandAndQueryAdaptersFollowRuntimeProfilesInSpringContext() {
        listOf("prod", "dev", "integration").forEach { profile ->
            AnnotationConfigApplicationContext().use { context ->
                context.environment.setActiveProfiles(profile)
                context.register(PersistenceAdapterTestConfiguration::class.java)
                context.refresh()

                assertNotNull(context.getBean(AccountCommandPort::class.java))
                assertNotNull(context.getBean(AccountQueryPort::class.java))
            }
        }

        AnnotationConfigApplicationContext().use { context ->
            context.environment.setActiveProfiles("test")
            context.register(PersistenceAdapterTestConfiguration::class.java)
            context.refresh()

            assertNull(context.getBeanProvider(AccountCommandPersistenceAdapter::class.java).ifAvailable)
            assertNull(context.getBeanProvider(AccountQueryPersistenceAdapter::class.java).ifAvailable)
        }
    }

    private fun profileOf(type: Class<*>): Set<String> =
        requireNotNull(type.getAnnotation(Profile::class.java)).value.toSet()

    @Configuration(proxyBeanMethods = false)
    @Import(AccountCommandPersistenceAdapter::class, AccountQueryPersistenceAdapter::class)
    private class PersistenceAdapterTestConfiguration {
        @Bean
        fun accountRepository(): AccountRepository = mock(AccountRepository::class.java)
    }
}
