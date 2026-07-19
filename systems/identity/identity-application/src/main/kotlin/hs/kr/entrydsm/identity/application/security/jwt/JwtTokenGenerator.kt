package hs.kr.entrydsm.identity.application.security.jwt

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JwtTokenGenerator(
    private val secret: String,
    private val issuer: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(secret.toByteArray(StandardCharsets.UTF_8).size >= MIN_SECRET_BYTES) {
            "JWT secret must be at least $MIN_SECRET_BYTES bytes."
        }
    }

    fun generateAccessToken(subject: String): JwtToken =
        generateToken(
            subject = subject,
            type = TokenType.ACCESS,
            expiresIn = ACCESS_TOKEN_TTL,
        )

    fun generateRefreshToken(subject: String): JwtToken =
        generateToken(
            subject = subject,
            type = TokenType.REFRESH,
            expiresIn = REFRESH_TOKEN_TTL,
        )

    private fun generateToken(
        subject: String,
        type: TokenType,
        expiresIn: Duration,
    ): JwtToken {
        require(subject.isNotBlank()) { "JWT subject must not be blank." }

        val issuedAt = Instant.now(clock)
        val expiresAt = issuedAt.plus(expiresIn)
        val header = mapOf(
            "alg" to "HS256",
            "typ" to "JWT",
        )
        val payload = mapOf(
            "iss" to issuer,
            "sub" to subject,
            "typ" to type.claimValue,
            "iat" to issuedAt.epochSecond,
            "exp" to expiresAt.epochSecond,
        )
        val signingInput = "${base64UrlEncode(json(header))}.${base64UrlEncode(json(payload))}"
        val signature = hmacSha256(signingInput)

        return JwtToken(
            value = "$signingInput.${base64UrlEncode(signature)}",
            type = type,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
    }

    private fun hmacSha256(value: String): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA_256)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA_256))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun base64UrlEncode(value: String): String =
        base64UrlEncode(value.toByteArray(StandardCharsets.UTF_8))

    private fun base64UrlEncode(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun json(values: Map<String, Any>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            val encodedValue = when (value) {
                is Number -> value.toString()
                else -> "\"${escapeJson(value.toString())}\""
            }
            "\"$key\":$encodedValue"
        }

    private fun escapeJson(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }

    companion object {
        val ACCESS_TOKEN_TTL: Duration = Duration.ofHours(2)
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(7)
        private const val HMAC_SHA_256 = "HmacSHA256"
        private const val MIN_SECRET_BYTES = 32
    }
}

data class JwtToken(
    val value: String,
    val type: TokenType,
    val issuedAt: Instant,
    val expiresAt: Instant,
)

enum class TokenType(
    val claimValue: String,
) {
    ACCESS("access"),
    REFRESH("refresh"),
}
