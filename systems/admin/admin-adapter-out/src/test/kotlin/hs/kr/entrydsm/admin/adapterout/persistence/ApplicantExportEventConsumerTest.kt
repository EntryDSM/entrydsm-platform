package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportProjectionJpaRepository
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.Base64
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.PendingMessages
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate

class ApplicantExportEventConsumerTest {
    @Test
    fun `재시작 후 자기 pending을 먼저 재처리하고 DLQ 장애에도 나머지를 ACK한다`() {
        val calls = mutableListOf<String>()
        val owner = Consumer.from("admin-applicant-export", "admin-1")
        var fail = true
        var deliveries = 0L
        var pending = false
        var fresh = true
        val failedEvent = ApplicantStatusChangedEvent.newBuilder().setApplicantId(1).setAccountId(10)
            .setVersion(1).setApplicantDeleted(true).build()
        val record = StreamRecords.string(mapOf("payload" to Base64.getEncoder().encodeToString(failedEvent.toByteArray())))
            .withStreamKey("events").withId(RecordId.of("1000-0"))
        val invalid = StreamRecords.string(mapOf("payload" to "%%%"))
            .withStreamKey("events").withId(RecordId.of("1000-1"))
        val valid = StreamRecords.string(mapOf("payload" to Base64.getEncoder().encodeToString(
            failedEvent.toBuilder().setApplicantId(2).setAccountId(20).build().toByteArray())))
            .withStreamKey("events").withId(RecordId.of("1000-2"))
        val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantExportProjectionJpaRepository::class.java)) { _, method, args ->
            when (method.name) {
                "findById" -> Optional.empty<ApplicantExportProjectionJpaEntity>()
                "applyVersion" -> {
                    val id = args[0] as Long
                    calls.add("commit:$id")
                    if (fail && id == 1L) throw IllegalStateException("DB unavailable")
                    1
                }
                else -> error(method.name)
            }
        } as ApplicantExportProjectionJpaRepository
        @Suppress("UNCHECKED_CAST")
        val operations = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(StreamOperations::class.java)) { _, method, args ->
            when (method.name) {
                "createGroup" -> throw IllegalStateException("BUSYGROUP")
                "read" -> {
                    assertEquals(owner, args[0])
                    val offset = (args[2] as Array<StreamOffset<String>>).single().offset.offset
                    calls.add("read:$offset")
                    if (offset == "0" && pending) {
                        deliveries++
                        listOf(record)
                    } else if (offset == ">" && fresh) {
                        fresh = false; pending = true; deliveries = 1
                        listOf(record, invalid, valid)
                    } else emptyList<Any>()
                }
                "pending" -> {
                    // 조회 조건에 minIdle이 있으면 자기 pending은 방금 읽었으므로 회수하지 않는다.
                    PendingMessages(owner.group, if (args.size == 5 || !pending) emptyList() else
                        listOf(PendingMessage(record.id, owner, Duration.ZERO, deliveries)))
                }
                "acknowledge" -> {
                    val id = (args[2] as Array<RecordId>).single()
                    calls.add("ack:${id.value}")
                    if (id == record.id) pending = false
                    1L
                }
                "add" -> { calls.add("dlq"); throw IllegalStateException("DLQ unavailable") }
                else -> error(method.name)
            }
        } as StreamOperations<String, String, String>
        val redis = object : StringRedisTemplate() {
            @Suppress("UNCHECKED_CAST")
            override fun <HK : Any?, HV : Any?> opsForStream(): StreamOperations<String, HK, HV> =
                operations as StreamOperations<String, HK, HV>
        }
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val store = ApplicantProjectionStore(repository, SnapshotCipher("test", mapOf("test" to key)))
        val metrics = SimpleMeterRegistry()
        fun consumer() = ApplicantExportEventConsumer(redis, store, metrics, "events", "admin-1")
        consumer().receive()
        assertTrue(pending)
        assertTrue(calls.contains("ack:1000-2"))
        assertFalse(calls.contains("ack:1000-0"))
        assertFalse(calls.contains("ack:1000-1"))
        // 같은 이름의 새 객체도 30초를 기다리지 않고 pending을 읽는다.
        calls.clear()
        val restarted = consumer()
        repeat(4) { restarted.receive() }
        assertEquals(5L, deliveries)
        assertTrue(calls.indexOf("commit:1") < calls.indexOf("read:>"))
        assertEquals(1, calls.count { it == "dlq" })
        assertTrue(pending)
        fail = false
        calls.clear()
        restarted.receive()
        assertEquals(listOf("read:0", "commit:1", "ack:1000-0", "read:>"), calls)
        assertFalse(pending)
    }
}
