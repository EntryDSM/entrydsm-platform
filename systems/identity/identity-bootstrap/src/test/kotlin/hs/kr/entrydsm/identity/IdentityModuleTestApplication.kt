package hs.kr.entrydsm.identity

import hs.kr.entrydsm.identity.config.IdentityModuleConfiguration
import java.time.Clock
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * identity 모듈만 올리는 테스트용 컨텍스트다.
 *
 * 운영에서는 platform 이 모든 모듈을 조립하고 `Clock` 같은 공용 빈을 제공한다.
 * 모듈 테스트는 그 역할을 여기서 대신한다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(IdentityModuleConfiguration::class)
class IdentityModuleTestApplication {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
