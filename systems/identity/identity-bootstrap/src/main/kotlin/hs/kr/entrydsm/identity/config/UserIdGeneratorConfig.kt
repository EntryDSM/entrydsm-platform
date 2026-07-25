package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.adapterout.persistence.MysqlUserIdGenerator
import hs.kr.entrydsm.identity.application.port.out.UserIdGenerator
import javax.sql.DataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class UserIdGeneratorConfig {
    @Bean
    fun userIdGenerator(dataSource: DataSource): UserIdGenerator =
        MysqlUserIdGenerator(dataSource)
}
