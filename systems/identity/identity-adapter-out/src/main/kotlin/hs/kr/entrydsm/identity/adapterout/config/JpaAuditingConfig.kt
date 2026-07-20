package hs.kr.entrydsm.identity.adapterout.config

import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
class JpaAuditingConfig
