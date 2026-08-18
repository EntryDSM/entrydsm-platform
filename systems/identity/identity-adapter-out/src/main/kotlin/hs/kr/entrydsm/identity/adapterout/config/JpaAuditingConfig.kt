package hs.kr.entrydsm.identity.adapterout.config

import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
@Profile("prod", "dev", "integration")
class JpaAuditingConfig
