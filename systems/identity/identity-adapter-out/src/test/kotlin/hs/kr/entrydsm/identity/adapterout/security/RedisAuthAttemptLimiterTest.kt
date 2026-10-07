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
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

class RedisAuthAttemptLimiterTest {
    private val template = mock(StringRedisTemplate::class.java)
    private val hasher = HmacLoginIdHasher("attempt-test-key")
    private val limiter = RedisAuthAttemptLimiter(template, hasher, "test", "issuer", 5, 900, 3, 60)

    @Test
    fun usesSeparateHashedKeysAndConfiguredLimits() {
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key("login-ip"))), eq("50"), eq("900")))
            .thenReturn(1L)
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key("login"))), eq("5"), eq("900")))
            .thenReturn(1L)
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key("password-reset"))), eq("3"), eq("60")))
            .thenReturn(1L)
        limiter.checkLogin("01012345678", "192.0.2.1")
        limiter.checkPasswordReset("01012345678")
        verify(template).execute(any<RedisScript<Long>>(), eq(listOf(key("login"))), eq("5"), eq("900"))
        verify(template).execute(any<RedisScript<Long>>(), eq(listOf(key("password-reset"))), eq("3"), eq("60"))
    }

    @Test
    fun exhaustedLimitReturns429AndRedisFailureReturns503() {
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key("login-ip"))), eq("50"), eq("900")))
            .thenReturn(1L)
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key("login"))), eq("5"), eq("900")))
            .thenReturn(0L)
            .thenThrow(DataAccessResourceFailureException("redis unavailable"))
        assertEquals(ErrorCode.AUTH_ATTEMPTS_EXCEEDED, assertThrows(IdentityDomainException::class.java) {
            limiter.checkLogin("01012345678", "192.0.2.1")
        }.errorCode)
        assertEquals(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE, assertThrows(IdentityDomainException::class.java) {
            limiter.checkLogin("01012345678", "192.0.2.1")
        }.errorCode)
    }

    @Test
    fun missingScriptResultFailsClosed() {
        assertEquals(ErrorCode.AUTH_ATTEMPT_STORE_UNAVAILABLE, assertThrows(IdentityDomainException::class.java) {
            limiter.checkPasswordReset("01012345678")
        }.errorCode)
    }

    @Test
    fun sourceLimitBlocksBeforeAccountLimit() {
        `when`(template.execute(any<RedisScript<Long>>(), eq(listOf(key("login-ip"))), eq("50"), eq("900")))
            .thenReturn(0L)
        assertEquals(ErrorCode.AUTH_ATTEMPTS_EXCEEDED, assertThrows(IdentityDomainException::class.java) {
            limiter.checkLogin("01012345678", "192.0.2.1")
        }.errorCode)
        verify(template).execute(any<RedisScript<Long>>(), eq(listOf(key("login-ip"))), eq("50"), eq("900"))
        verifyNoMoreInteractions(template)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroAttemptLimit() {
        RedisAuthAttemptLimiter(template, hasher, "test", "issuer", 0, 900, 3, 60)
    }

    private fun key(operation: String) = "test:issuer:identity:auth-attempt:$operation:${hasher.hash(when (operation) { "login-ip" -> "192.0.2.1"; "login" -> "192.0.2.1\u000001012345678"; else -> "01012345678" })}"
}
