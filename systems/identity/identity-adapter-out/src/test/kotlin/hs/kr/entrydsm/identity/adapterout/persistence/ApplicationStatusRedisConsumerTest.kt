package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.application.grpc.ApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import io.lettuce.core.RedisBusyException
import java.lang.reflect.Proxy
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamRecords
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

    @Test
    fun failingPendingEventDoesNotBlockNewEvents() {
        val pending = listOf(record("1-0", accountId = FAILING_ACCOUNT))
        val new = listOf(record("2-0", accountId = 1))
        val acked = mutableListOf<String>()
        val redis = mock(StringRedisTemplate::class.java)
        `when`(redis.opsForStream<String, String>()).thenReturn(fakeStream(pending, new, acked))
        val consumed = mutableListOf<Long>()
        val eventConsumer = object : ApplicationEventConsumer {
            override fun consume(event: ApplicationStateChangedEvent): Boolean {
                consumed += event.userId
                check(event.userId != FAILING_ACCOUNT) { "consume failed" }
                return true
            }
        }

        ApplicationStatusRedisConsumer(redis, eventConsumer, STREAM, GROUP, "consumer").poll()

        assertEquals(listOf(FAILING_ACCOUNT, 1L), consumed)
        assertEquals(listOf("2-0"), acked)
    }

    /** pending(`0`) 읽기엔 [pending], 새 이벤트(`>`) 읽기엔 [new] 를 주고 ack 한 ID 를 [acked] 에 모은다. */
    @Suppress("UNCHECKED_CAST")
    private fun fakeStream(
        pending: List<Any>,
        new: List<Any>,
        acked: MutableList<String>,
    ) = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(StreamOperations::class.java)) { _, method, args ->
        when (method.name) {
            "read" -> if (((args[2] as Array<*>)[0] as StreamOffset<*>).offset == ReadOffset.from("0")) pending else new
            "acknowledge" -> (args[2] as Array<*>).also { ids -> ids.forEach { acked += it.toString() } }.size.toLong()
            else -> "OK"
        }
    } as StreamOperations<String, String, String>

    private fun record(id: String, accountId: Long) = StreamRecords.newRecord()
        .`in`(STREAM)
        .withId(RecordId.of(id))
        .ofMap(
            mapOf(
                "eventId" to id,
                "payload" to Base64.getEncoder().encodeToString(
                    ApplicantStatusChangedEvent.newBuilder()
                        .setEventId(id)
                        .setAccountId(accountId)
                        .setApplicantId(accountId)
                        .setApplicantStatus(ApplicantStatus.APPLICANT_STATUS_SUBMITTED)
                        .setPassStatus(PassStatus.PASS_STATUS_NOT_ANNOUNCED)
                        .setVersion(2)
                        .build()
                        .toByteArray(),
                ),
            ),
        )

    private companion object {
        const val STREAM = "application.applicant-status"
        const val GROUP = "identity"
        const val FAILING_ACCOUNT = 999L
    }
}
