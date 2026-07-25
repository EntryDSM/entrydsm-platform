package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.UserIdGenerator
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.service.AuthService
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class IdentityApplicationConfig {
    @Bean
    fun authService(
        accountQueryPort: AccountQueryPort,
        accountCommandPort: AccountCommandPort,
        accountRegistrationPort: AccountRegistrationPort,
        userIdGenerator: UserIdGenerator,
        passwordHasher: PasswordHasher,
        jwtTokenGenerator: JwtTokenGenerator,
        clock: Clock,
    ): AuthService =
        AuthService(
            accountQueryPort,
            accountCommandPort,
            accountRegistrationPort,
            userIdGenerator,
            passwordHasher,
            jwtTokenGenerator,
            clock,
        )
}
