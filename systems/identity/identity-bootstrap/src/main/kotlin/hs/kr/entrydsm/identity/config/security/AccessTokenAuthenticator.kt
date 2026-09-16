package hs.kr.entrydsm.identity.config.security

import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenRevocationStore
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date
import org.springframework.stereotype.Component

/** 접근 토큰이 가리키는 계정. 게이트웨이가 `/accounts/me/authority` 로 받아 가던 값과 같다. */
data class AuthenticatedAccount(
    val userId: Long,
    val role: Role,
    val isSensitiveAgree: Boolean,
)

class AccessTokenInvalidException(cause: Throwable? = null) : RuntimeException(cause)

/**
 * 접근 토큰을 검증하고 계정 상태까지 확인한다.
 *
 * identity 경로를 지키는 [JwtFilter] 와 다른 모듈 경로에 신뢰 헤더를 채우는
 * [hs.kr.entrydsm.identity.config.edge.EdgeAccessFilter] 가 같은 판단을 쓰도록 한 곳에 둔다.
 */
@Component
class AccessTokenAuthenticator(
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
    private val accountQueryPort: AccountQueryPort,
    private val refreshTokenRevocationStore: RefreshTokenRevocationStore,
) {
    private val signingKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(StandardCharsets.UTF_8))
    private val jwtParser = Jwts.parser()
        .verifyWith(signingKey)
        .clock({ Date.from(Instant.now(clock)) })
        .build()

    /**
     * @throws AccessTokenInvalidException 서명·발급자·유형·만료·토큰 버전·계정 상태 중 하나라도 어긋날 때
     * @throws hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException 토큰 버전 저장소를 못 읽을 때
     */
    fun authenticate(token: String): AuthenticatedAccount = try {
        val signedJwt = jwtParser.parseSignedClaims(token)
        if (signedJwt.header.algorithm != Jwts.SIG.HS256.id) throw AccessTokenInvalidException()

        val claims = signedJwt.payload
        if (claims.issuer != jwtProperties.issuer) throw AccessTokenInvalidException()
        if (claims[JwtTokenGenerator.TOKEN_TYPE_CLAIM] as? String != ACCESS_TOKEN_TYPE) {
            throw AccessTokenInvalidException()
        }

        val subject = claims.subject?.takeIf { it.isNotBlank() } ?: throw AccessTokenInvalidException()
        val userId = subject
            .removePrefix(USER_PRINCIPAL_PREFIX)
            .takeIf { subject.startsWith(USER_PRINCIPAL_PREFIX) }
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: throw AccessTokenInvalidException()
        val expiration = claims.expiration?.toInstant() ?: throw AccessTokenInvalidException()
        if (!expiration.isAfter(Instant.now(clock))) throw AccessTokenInvalidException()
        val tokenVersion = (claims[JwtTokenGenerator.TOKEN_VERSION_CLAIM] as? Number)
            ?.toLong()
            ?.takeIf { it >= JwtTokenGenerator.INITIAL_TOKEN_VERSION }
            ?: throw AccessTokenInvalidException()
        if (refreshTokenRevocationStore.currentVersion(userId) != tokenVersion) {
            throw AccessTokenInvalidException()
        }
        val account = accountQueryPort.findByUserId(userId) ?: throw AccessTokenInvalidException()
        if (account.status != AccountStatus.ACTIVE) throw AccessTokenInvalidException()
        AuthenticatedAccount(userId = userId, role = account.role, isSensitiveAgree = account.isSensitiveAgree)
    } catch (exception: AccessTokenInvalidException) {
        throw exception
    } catch (exception: JwtException) {
        throw AccessTokenInvalidException(exception)
    } catch (exception: IllegalArgumentException) {
        throw AccessTokenInvalidException(exception)
    }

    companion object {
        const val ACCESS_TOKEN_TYPE = "access"
        const val USER_PRINCIPAL_PREFIX = "user_"
    }
}
