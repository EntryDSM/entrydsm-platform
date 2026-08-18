package hs.kr.entrydsm.identity.config

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.identity.application.port.`in`.PassPort
import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistrationPort
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.application.port.out.PasswordResetOwnershipVerifier
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassCallbackTokenStore
import hs.kr.entrydsm.identity.application.port.out.PassProviderPort
import hs.kr.entrydsm.identity.application.port.out.SignupOwnershipVerifier
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRotationStore
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifier
import hs.kr.entrydsm.identity.application.service.ApplicationService
import hs.kr.entrydsm.identity.application.service.AuthService
import hs.kr.entrydsm.identity.application.service.AccountService
import hs.kr.entrydsm.identity.application.service.PassService
import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value

@Configuration(proxyBeanMethods = false)
class IdentityApplicationConfig {
    @Bean
    fun applicationService(
        applicationDataPort: ApplicationDataPort,
        clock: Clock,
    ): ApplicationPort = ApplicationService(applicationDataPort, clock)

    @Bean
    fun accountService(
        accountQueryPort: AccountQueryPort,
        accountCommandPort: AccountCommandPort,
        applicationDataPort: ApplicationDataPort,
        clock: Clock,
    ): AccountPort = AccountService(
        accountQueryPort,
        accountCommandPort,
        applicationDataPort,
        clock,
    )

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
        passwordResetOwnershipVerifier: PasswordResetOwnershipVerifier,
        signupOwnershipVerifier: SignupOwnershipVerifier,
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
            passwordResetOwnershipVerifier,
            signupOwnershipVerifier,
        )

    @Bean
    fun passService(
        passProviderPort: PassProviderPort,
        passProofStore: PassProofStore,
        passCallbackTokenStore: PassCallbackTokenStore,
        @Value("\${pass.proof-ttl-seconds:300}") proofTtlSeconds: Long,
        @Value("\${pass.allowed-redirect-origins:\${pass.base-url:http://localhost:3000}}")
        allowedRedirectOrigins: String,
    ): PassPort = PassService(
        passProviderPort,
        passProofStore,
        passCallbackTokenStore,
        proofTtlSeconds,
        allowedRedirectOrigins,
    )
}
