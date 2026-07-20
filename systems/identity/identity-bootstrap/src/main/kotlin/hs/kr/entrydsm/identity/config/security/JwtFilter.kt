package hs.kr.entrydsm.identity.config.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtFilter(
    private val jwtProperties: JwtProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)
        if (token == null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val subject = verify(token)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(subject, null, emptyList())
            filterChain.doFilter(request, response)
        } catch (exception: JwtValidationException) {
            SecurityContextHolder.clearContext()
            authenticationEntryPoint.commence(
                request,
                response,
                BadCredentialsException("Invalid JWT", exception),
            )
        }
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val authorization = request.getHeader("Authorization")
        if (authorization?.startsWith(BEARER_PREFIX) == true) {
            return authorization.removePrefix(BEARER_PREFIX).takeIf { it.isNotBlank() }
        }
        return request.cookies
            ?.firstOrNull { it.name == ACCESS_TOKEN_COOKIE }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun verify(token: String): String = try {
        val parts = token.split('.')
        if (parts.size != JWT_PART_COUNT) throw JwtValidationException()

        val header = decodeJson(parts[0])
        val payload = decodeJson(parts[1])
        if (header["alg"]?.asText() != ALGORITHM) throw JwtValidationException()
        if (payload["iss"]?.asText() != jwtProperties.issuer) throw JwtValidationException()
        if (payload["typ"]?.asText() != ACCESS_TOKEN_TYPE) throw JwtValidationException()

        val subject = payload["sub"]?.asText()?.takeIf { it.isNotBlank() }
            ?: throw JwtValidationException()
        val expiration = payload["exp"]?.asLong() ?: throw JwtValidationException()
        if (expiration <= Instant.now(clock).epochSecond) throw JwtValidationException()

        val signature = decode(parts[2])
        val expectedSignature = sign("${parts[0]}.${parts[1]}")
        if (!MessageDigest.isEqual(signature, expectedSignature)) throw JwtValidationException()
        subject
    } catch (exception: JwtValidationException) {
        throw exception
    } catch (exception: Exception) {
        throw JwtValidationException(exception)
    }

    private fun decodeJson(value: String) =
        objectMapper.readTree(String(decode(value), StandardCharsets.UTF_8))

    private fun decode(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)

    private fun sign(value: String): ByteArray {
        val mac = Mac.getInstance(ALGORITHM_NAME)
        mac.init(
            SecretKeySpec(
                jwtProperties.secret.toByteArray(StandardCharsets.UTF_8),
                ALGORITHM_NAME,
            )
        )
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }

    private class JwtValidationException(cause: Throwable? = null) : RuntimeException(cause)

    companion object {
        private const val ACCESS_TOKEN_COOKIE = "access_token"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val ALGORITHM = "HS256"
        private const val ALGORITHM_NAME = "HmacSHA256"
        private const val BEARER_PREFIX = "Bearer "
        private const val JWT_PART_COUNT = 3
    }
}
