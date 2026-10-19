package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

class RedisAuthAttemptLimiterTest {
    private val template = mock(StringRedisTemplate::class.java)
    private val hasher = HmacLoginIdHasher("attempt-test-key")
    private val limiter = RedisAuthAttemptLimiter(template, hasher, "test", "issuer", 30, 300, 3, 60)

    @Test
    fun failureUsesHashedScopeKeysAndConfiguredFailureLimits() {
        `when`(template.execute(any<RedisScript<Long>>(), eq(loginKeys()), eq("30"), eq("300"), eq("300"))).thenReturn(1L)
        limiter.recordLoginFailure("01012345678", "192.0.2.1")
        verify(template).execute(any<RedisScript<Long>>(), eq(loginKeys()), eq("30"), eq("300"), eq("300"))
    }

    @Test
    fun loginCheckAndSuccessMapLockAndStoreFailure() {
        `when`(template.execute(any<RedisScript<Long>>(), eq(loginKeys())))
            .thenReturn(0L)
            .thenThrow(DataAccessResourceFailureException("redis unavailable"))
        assertEquals(ErrorCode.AUTH_ATTEMPTS_EXCEEDED, assertThrows(IdentityDomainException::class.java) {
            limiter.checkLogin("01012345678", "192.0.2.1")
        }.errorCode)
        assertEquals(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE, assertThrows(IdentityDomainException::class.java) {
            limiter.recordLoginSuccess("01012345678", "192.0.2.1")
        }.errorCode)
    }

    @Test
    fun passwordResetRetainsItsOwnKeysAndLimits() {
        val key = "test:issuer:identity:auth-attempt:password-reset:${hasher.hash("01012345678")}"
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key)), eq("3"), eq("60"))).thenReturn(1L)
        limiter.checkPasswordReset("01012345678")
        verify(template).execute(any<RedisScript<Long>>(), eq(listOf(key)), eq("3"), eq("60"))
    }

    @Test
    fun missingScriptResultFailsClosed() {
        assertEquals(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE, assertThrows(IdentityDomainException::class.java) {
            limiter.checkPasswordReset("01012345678")
        }.errorCode)
        assertEquals(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE, assertThrows(IdentityDomainException::class.java) {
            limiter.checkLogin("01012345678", "192.0.2.1")
        }.errorCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroAttemptLimit() {
        RedisAuthAttemptLimiter(template, hasher, "test", "issuer", 0, 300, 3, 60)
    }

    private fun loginKeys(): List<String> {
        val pair = "test:issuer:identity:auth-attempt:login-failure-v2:${hasher.hash("192.0.2.1\u000001012345678")}"
        val ip = "test:issuer:identity:auth-attempt:login-ip-failure-v2:${hasher.hash("192.0.2.1")}"
        return listOf("$pair:count", "$ip:count", "$pair:lock", "$ip:lock")
    }
}
