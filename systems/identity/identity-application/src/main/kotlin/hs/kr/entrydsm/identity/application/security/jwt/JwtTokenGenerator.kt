package hs.kr.entrydsm.identity.application.security.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
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

    fun generateAccessToken(subject: String): JwtToken =
        generateToken(subject = subject, type = TokenType.ACCESS, expiresIn = ACCESS_TOKEN_TTL)

    fun generateRefreshToken(subject: String): JwtToken =
        generateToken(subject = subject, type = TokenType.REFRESH, expiresIn = REFRESH_TOKEN_TTL)

    private fun generateToken(
        subject: String,
        type: TokenType,
        expiresIn: Duration,
    ): JwtToken {
        require(subject.isNotBlank()) { "JWT subject must not be blank." }

        val issuedAt = Instant.now(clock)
        val expiresAt = issuedAt.plus(expiresIn)
        val value = Jwts.builder()
            .issuer(issuer)
            .subject(subject)
            .claim(TOKEN_TYPE_CLAIM, type.claimValue)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
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
        const val USER_PRINCIPAL_PREFIX = "user_"
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
        "JwtToken(type=$type, issuedAt=$issuedAt, expiresAt=$expiresAt, value=[REDACTED])"
}

enum class TokenType(
    val claimValue: String,
) {
    ACCESS("access"),
    REFRESH("refresh"),
}
