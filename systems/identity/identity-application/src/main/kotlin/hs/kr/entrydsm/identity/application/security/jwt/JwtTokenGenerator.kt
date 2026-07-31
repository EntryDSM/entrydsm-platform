package hs.kr.entrydsm.identity.application.security.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import hs.kr.entrydsm.identity.application.security.SensitiveValueMasker.REDACTED
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

class JwtTokenGenerator(
    private val secret: String,
    private val issuer: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val secretBytes = secret.toByteArray(StandardCharsets.UTF_8)

    init {
        require(secretBytes.size >= MIN_SECRET_BYTES) {
            "JWT secret must be at least $MIN_SECRET_BYTES bytes."
        }
    }

    private val signingKey = Keys.hmacShaKeyFor(secretBytes)

    fun generateAccessToken(
        subject: String,
        tokenVersion: Long = INITIAL_TOKEN_VERSION,
    ): JwtToken {
        require(tokenVersion >= INITIAL_TOKEN_VERSION) { "JWT token version must not be negative." }
        return generateToken(
            subject = subject,
            type = TokenType.ACCESS,
            expiresIn = ACCESS_TOKEN_TTL,
            tokenVersion = tokenVersion,
        )
    }

    fun generateRefreshToken(subject: String, tokenVersion: Long = INITIAL_TOKEN_VERSION): JwtToken {
        require(tokenVersion >= INITIAL_TOKEN_VERSION) { "JWT token version must not be negative." }
        return generateToken(
            subject = subject,
            type = TokenType.REFRESH,
            expiresIn = REFRESH_TOKEN_TTL,
            tokenVersion = tokenVersion,
        )
    }

    private fun generateToken(
        subject: String,
        type: TokenType,
        expiresIn: Duration,
        tokenVersion: Long? = null,
    ): JwtToken {
        require(subject.isNotBlank()) { "JWT subject must not be blank." }

        val issuedAt = Instant.now(clock)
        val expiresAt = issuedAt.plus(expiresIn)
        val builder = Jwts.builder()
            .issuer(issuer)
            .subject(subject)
            .claim(TOKEN_TYPE_CLAIM, type.claimValue)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
        if (tokenVersion != null) {
            builder.claim(TOKEN_VERSION_CLAIM, tokenVersion)
        }
        val value = builder
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact()

        return JwtToken(
            value = value,
            type = type,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
    }

    companion object {
        val ACCESS_TOKEN_TTL: Duration = Duration.ofHours(2)
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(7)
        const val TOKEN_TYPE_CLAIM = "typ"
        const val TOKEN_VERSION_CLAIM = "token_version"
        const val USER_PRINCIPAL_PREFIX = "user_"
        const val INITIAL_TOKEN_VERSION = 0L
        private const val MIN_SECRET_BYTES = 32
    }
}

data class JwtToken(
    val value: String,
    val type: TokenType,
    val issuedAt: Instant,
    val expiresAt: Instant,
) {
    override fun toString(): String =
        "JwtToken(type=$type, issuedAt=$issuedAt, expiresAt=$expiresAt, value=$REDACTED)"
}

enum class TokenType(
    val claimValue: String,
) {
    ACCESS("access"),
    REFRESH("refresh"),
}
