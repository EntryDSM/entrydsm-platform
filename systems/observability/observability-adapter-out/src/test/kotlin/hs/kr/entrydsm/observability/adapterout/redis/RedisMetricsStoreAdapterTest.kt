package hs.kr.entrydsm.observability.adapterout.redis

import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

class RedisMetricsStoreAdapterTest {
    private val from = Instant.parse("2026-09-01T00:00:00Z")
    private val to = from.plus(Duration.ofDays(5)) // 5분 버킷 1,440개

    @Test
    fun sumsBucketsWithoutOneRoundTripPerBucket() {
        val redis = FakeRedis { key -> if (key.contains(":success:")) "2" else null }
        val adapter = RedisMetricsStoreAdapter(redis)

        assertEquals(2880L, adapter.apiRequestCount(from, to, success = true))
        assertEquals(2880L, adapter.apiRequestCount(from, to))
        assertEquals(0L, adapter.businessCount("pdf-download", from, to, success = false))
        // 키 1,440·2,880·1,440개를 1,000개씩 읽는다.
        assertEquals(7, redis.roundTrips)
    }

    private class FakeRedis(private val valueOf: (String) -> String?) : StringRedisTemplate() {
        var roundTrips = 0

        @Suppress("UNCHECKED_CAST")
        override fun opsForValue(): ValueOperations<String, String> =
            Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ValueOperations::class.java)) { _, method, args ->
                check(method.name == "multiGet") { "unexpected call: ${method.name}" }
                roundTrips++
                (args[0] as Collection<String>).map(valueOf)
            } as ValueOperations<String, String>
    }
}
