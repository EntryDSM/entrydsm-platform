package hs.kr.entrydsm.identity.adapterin.web.exception

import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.http.HttpStatus

class RedisUnavailableExceptionHandlerTest {
    @Test
    fun redisFailureIsReturnedAsServiceUnavailable() {
        val response = RedisUnavailableExceptionHandler().handle(
            RefreshTokenStoreUnavailableException(IllegalStateException("redis unavailable")),
        )

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("REDIS_UNAVAILABLE", response.body?.error?.code)
    }
}
