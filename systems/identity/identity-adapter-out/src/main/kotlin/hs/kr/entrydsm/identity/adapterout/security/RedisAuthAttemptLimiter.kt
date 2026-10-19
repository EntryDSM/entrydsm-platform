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
    @Value("\${auth.login.max-attempts:30}") private val loginMaxAttempts: Int,
    @Value("\${auth.login.window-seconds:300}") private val loginWindowSeconds: Long,
    @Value("\${auth.password-reset.max-attempts:5}") private val resetMaxAttempts: Int,
    @Value("\${auth.password-reset.window-seconds:900}") private val resetWindowSeconds: Long,
    @Value("\${auth.login.ip-max-attempts:300}") private val loginIpMaxAttempts: Int = 300,
) : AuthAttemptLimiter {
    init {
        require(namespace.isNotBlank() && issuer.isNotBlank())
        require(loginIpMaxAttempts > 0 && loginMaxAttempts > 0 && resetMaxAttempts > 0)
        require(loginWindowSeconds > 0 && resetWindowSeconds > 0)
    }

    // 실패 횟수를 늘리지 않고 이미 생성된 차단만 검사한다.
    override fun checkLogin(loginId: String, clientIp: String) {
        execute(LOGIN_CHECK_SCRIPT, loginKeys(loginId, clientIp))
    }

    // 실패 누적과 임계 도달 시점의 차단을 Redis에서 원자적으로 기록한다.
    override fun recordLoginFailure(loginId: String, clientIp: String) {
        execute(
            LOGIN_FAILURE_SCRIPT,
            loginKeys(loginId, clientIp),
            loginMaxAttempts.toString(), loginIpMaxAttempts.toString(), loginWindowSeconds.toString(),
        )
    }

    // 동시에 생성된 차단을 재확인하고 계정별 연속 실패만 초기화한다.
    override fun recordLoginSuccess(loginId: String, clientIp: String) {
        execute(LOGIN_SUCCESS_SCRIPT, loginKeys(loginId, clientIp))
    }

    // 비밀번호 재설정은 기존 시도 횟수 제한을 유지한다.
    override fun checkPasswordReset(loginId: String) {
        execute(
            ATTEMPT_SCRIPT,
            listOf("$namespace:$issuer:identity:auth-attempt:password-reset:${loginIdHasher.hash(loginId)}"),
            resetMaxAttempts.toString(), resetWindowSeconds.toString(),
        )
    }

    // 기존 전체 시도 카운터와 실패 카운터가 섞이지 않도록 키 버전을 분리한다.
    private fun loginKeys(loginId: String, clientIp: String): List<String> {
        require(clientIp.isNotBlank())
        val pair = "$namespace:$issuer:identity:auth-attempt:login-failure-v2:${loginIdHasher.hash("$clientIp\u0000$loginId")}"
        val ip = "$namespace:$issuer:identity:auth-attempt:login-ip-failure-v2:${loginIdHasher.hash(clientIp)}"
        return listOf("$pair:count", "$ip:count", "$pair:lock", "$ip:lock")
    }

    // 저장소 오류와 차단 응답을 일관된 도메인 오류로 변환한다.
    private fun execute(script: DefaultRedisScript<Long>, keys: List<String>, vararg args: String) {
        val result = try {
            redisTemplate.execute(script, keys, *args)
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
        val LOGIN_CHECK_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[3], KEYS[4]) > 0 then
                return 0
            end
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        val LOGIN_FAILURE_SCRIPT = DefaultRedisScript(
            """
            for i = 1, 2 do
                if redis.call('EXISTS', KEYS[i + 2]) == 0 then
                    local count = redis.call('INCR', KEYS[i])
                    if count >= tonumber(ARGV[i]) then
                        redis.call('SET', KEYS[i + 2], '1', 'EX', ARGV[3])
                        redis.call('DEL', KEYS[i])
                    else
                        redis.call('EXPIRE', KEYS[i], ARGV[3])
                    end
                end
            end
            return 1
            """.trimIndent(),
            Long::class.java,
        )
        val LOGIN_SUCCESS_SCRIPT = DefaultRedisScript(
            """
            if redis.call('EXISTS', KEYS[3], KEYS[4]) > 0 then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """.trimIndent(),
            Long::class.java,
        )
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
