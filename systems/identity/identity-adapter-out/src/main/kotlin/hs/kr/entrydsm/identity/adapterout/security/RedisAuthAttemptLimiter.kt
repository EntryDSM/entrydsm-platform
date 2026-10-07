package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.AuthAttemptLimiter
import hs.kr.entrydsm.identity.application.port.out.LoginIdHasher
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

@Component
class RedisAuthAttemptLimiter(
    private val redisTemplate: StringRedisTemplate,
    private val loginIdHasher: LoginIdHasher,
    @Value("\${identity.redis.namespace}") private val namespace: String,
    @Value("\${auth.jwt.issuer}") private val issuer: String,
    @Value("\${auth.login.max-attempts:5}") private val loginMaxAttempts: Int,
    @Value("\${auth.login.window-seconds:900}") private val loginWindowSeconds: Long,
    @Value("\${auth.password-reset.max-attempts:5}") private val resetMaxAttempts: Int,
    @Value("\${auth.password-reset.window-seconds:900}") private val resetWindowSeconds: Long,
    @Value("\${auth.login.ip-max-attempts:50}") private val loginIpMaxAttempts: Int = 50,
) : AuthAttemptLimiter {
    init {
        require(namespace.isNotBlank() && issuer.isNotBlank())
        require(loginIpMaxAttempts > 0 && loginMaxAttempts > 0 && resetMaxAttempts > 0)
        require(loginWindowSeconds > 0 && resetWindowSeconds > 0)
    }

    override fun checkLogin(loginId: String, clientIp: String) {
        require(clientIp.isNotBlank())
        check("login-ip", clientIp, loginIpMaxAttempts, loginWindowSeconds)
        check("login", "$clientIp\u0000$loginId", loginMaxAttempts, loginWindowSeconds)
    }

    override fun checkPasswordReset(loginId: String) = check("password-reset", loginId, resetMaxAttempts, resetWindowSeconds)

    private fun check(operation: String, loginId: String, maxAttempts: Int, windowSeconds: Long) {
        val result = try {
            redisTemplate.execute(
                ATTEMPT_SCRIPT,
                listOf("$namespace:$issuer:identity:auth-attempt:$operation:${loginIdHasher.hash(loginId)}"),
                maxAttempts.toString(),
                windowSeconds.toString(),
            )
        } catch (exception: DataAccessException) {
            throw IdentityDomainException(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE, exception)
        }
        when (result) {
            1L -> return
            0L -> throw IdentityDomainException(ErrorCode.AUTH_ATTEMPTS_EXCEEDED)
            else -> throw IdentityDomainException(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE)
        }
    }

    private companion object {
        val ATTEMPT_SCRIPT = DefaultRedisScript(
            """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then
                return 0
            end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}
