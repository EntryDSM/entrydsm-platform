package hs.kr.entrydsm.identity.application.security.jwt

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date

class JwtTokenVerifier(
    secret: String,
    private val issuer: String,
    private val clock: Clock,
) {
    private val signingKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    private val jwtParser = Jwts.parser()
        .verifyWith(signingKey)
        .clock(io.jsonwebtoken.Clock { Date.from(Instant.now(clock)) })
        .build()

    fun verifyRefreshToken(token: String): VerifiedRefreshToken = try {
        val signedJwt = jwtParser.parseSignedClaims(token)
        if (signedJwt.header.algorithm != Jwts.SIG.HS256.id) {
            throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        }

        val claims = signedJwt.payload
        if (claims.issuer != issuer ||
            claims[JwtTokenGenerator.TOKEN_TYPE_CLAIM] as? String != TokenType.REFRESH.claimValue
        ) {
            throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        }

        val subject = claims.subject?.takeIf { it.isNotBlank() }
            ?: throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        val userId = subject
            .removePrefix(JwtTokenGenerator.USER_PRINCIPAL_PREFIX)
            .takeIf { subject.startsWith(JwtTokenGenerator.USER_PRINCIPAL_PREFIX) }
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        val tokenId = claims.id?.takeIf { it.isNotBlank() }
            ?: throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        val tokenVersion = (claims[JwtTokenGenerator.TOKEN_VERSION_CLAIM] as? Number)?.toLong()
            ?.takeIf { it >= JwtTokenGenerator.INITIAL_TOKEN_VERSION }
            ?: throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        val expiresAt = claims.expiration?.toInstant()
            ?: throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID)
        if (!expiresAt.isAfter(Instant.now(clock))) {
            throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.EXPIRED)
        }

        VerifiedRefreshToken(
            userId = userId,
            tokenId = tokenId,
            tokenVersion = tokenVersion,
            expiresAt = expiresAt,
        )
    } catch (exception: JwtTokenVerificationException) {
        throw exception
    } catch (exception: ExpiredJwtException) {
        throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.EXPIRED, exception)
    } catch (exception: JwtException) {
        throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID, exception)
    } catch (exception: IllegalArgumentException) {
        throw JwtTokenVerificationException(JwtTokenVerificationException.Reason.INVALID, exception)
    }
}

data class VerifiedRefreshToken(
    val userId: Long,
    val tokenId: String,
    val tokenVersion: Long,
    val expiresAt: Instant,
)

class JwtTokenVerificationException(
    val reason: Reason,
    cause: Throwable? = null,
) : RuntimeException(cause) {
    enum class Reason {
        INVALID,
        EXPIRED,
    }
}
