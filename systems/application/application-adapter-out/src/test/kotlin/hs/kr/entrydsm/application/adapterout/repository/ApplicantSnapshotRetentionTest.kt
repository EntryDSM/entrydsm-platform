package hs.kr.entrydsm.application.adapterout.repository

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.connection.stream.StreamInfo
import org.springframework.data.redis.connection.stream.PendingMessagesSummary
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.domain.Range

class ApplicantSnapshotRetentionTest {
    @Test
    fun `모든 소비자가 확인한 오래된 이벤트만 제거하고 pending 이벤트는 남긴다`() {
        var outboxCleaned = false
        val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantStatusOutboxJpaRepository::class.java)) { _, method, _ ->
            assertEquals("deletePublishedBefore", method.name); outboxCleaned = true; 1
        } as ApplicantStatusOutboxJpaRepository
        val records = listOf("1000-1", "1000-2", "1000-10", "1001-0").map {
            StreamRecords.string(mapOf("payload" to "encrypted")).withStreamKey("events").withId(RecordId.of(it))
        }
        val groups = StreamInfo.XInfoGroups.fromList(listOf(
            listOf("name", "admin", "consumers", 1L, "pending", 1L, "last-delivered-id", "1001-0"),
            listOf("name", "identity", "consumers", 1L, "pending", 0L, "last-delivered-id", "1001-0")))
        val deleted = mutableListOf<String>()
        @Suppress("UNCHECKED_CAST")
        val operations = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(StreamOperations::class.java)) { _, method, args ->
            when (method.name) {
                "groups" -> groups
                "pending" -> if (args[1] == "admin") PendingMessagesSummary("admin", 1, Range.closed("1000-10", "1000-10"), mapOf("worker" to 1L))
                    else PendingMessagesSummary("identity", 0, Range.unbounded<String>(), emptyMap())
                "range" -> records
                "delete" -> { (args[1] as Array<RecordId>).forEach { deleted.add(it.value) }; deleted.size.toLong() }
                else -> error(method.name)
            }
        } as StreamOperations<String, String, String>
        val redis = object : StringRedisTemplate() {
            @Suppress("UNCHECKED_CAST")
            override fun <HK : Any?, HV : Any?> opsForStream(): StreamOperations<String, HK, HV> = operations as StreamOperations<String, HK, HV>
        }
        ApplicantSnapshotRetention(repository, redis, "events", 7).cleanup()
        assertTrue(outboxCleaned)
        assertEquals(listOf("1000-1", "1000-2"), deleted)
    }
}
