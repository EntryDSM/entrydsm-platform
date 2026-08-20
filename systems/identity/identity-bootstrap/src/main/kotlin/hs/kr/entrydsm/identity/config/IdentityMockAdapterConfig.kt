package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.application.mock.MockApplicationDataAdapter
import hs.kr.entrydsm.identity.application.mock.MockAuthAccountRepositoryAdapter
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@Profile("test")
class IdentityMockAdapterConfig {
    @Bean
    fun mockApplicationDataAdapter(): ApplicationDataPort = MockApplicationDataAdapter()

    @Bean
    fun mockAuthAccountRepositoryAdapter(): AccountRepository = MockAuthAccountRepositoryAdapter()
}
