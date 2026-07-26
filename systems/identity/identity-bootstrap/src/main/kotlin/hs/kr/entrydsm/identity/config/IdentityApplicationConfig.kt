package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRotationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifier
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
        passwordHasher: PasswordHasher,
        jwtTokenGenerator: JwtTokenGenerator,
        jwtTokenVerifier: JwtTokenVerifier,
        refreshTokenRotationStore: RefreshTokenRotationStore,
        refreshTokenRevocationStore: RefreshTokenRevocationStore,
        clock: Clock,
    ): AuthService =
        AuthService(
            accountQueryPort,
            accountCommandPort,
            accountRegistrationPort,
            passwordHasher,
            jwtTokenGenerator,
            jwtTokenVerifier,
            refreshTokenRotationStore,
            refreshTokenRevocationStore,
            clock,
        )
}
