package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import io.lettuce.core.RedisBusyException
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate

class ApplicationStatusRedisConsumerTest {
    @Test
    fun existingConsumerGroupDoesNotFailPolling() {
        val redis = mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val streamOperations = mock(StreamOperations::class.java) as StreamOperations<String, String, String>
        `when`(redis.opsForStream<String, String>()).thenReturn(streamOperations)
        `when`(streamOperations.createGroup(STREAM, ReadOffset.from("0"), GROUP)).thenThrow(
            RedisSystemException("Error in execution", RedisBusyException("BUSYGROUP Consumer Group name already exists")),
        )

        ApplicationStatusRedisConsumer(redis, mock(ApplicationEventConsumer::class.java), STREAM, GROUP, "consumer").poll()
    }

    private companion object {
        const val STREAM = "application.applicant-status"
        const val GROUP = "identity"
    }
}
