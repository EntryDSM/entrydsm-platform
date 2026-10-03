package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportProjectionJpaRepository
import hs.kr.entrydsm.application.grpc.ApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import com.google.protobuf.ByteString
import java.lang.reflect.Proxy
import java.sql.DriverManager
import java.util.Base64
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.springframework.data.jpa.repository.Query

class ApplicantProjectionStoreTest {
    @Test
    fun `실제 upsert SQL로 중복 역순 삭제 재접수와 암호화를 검증한다`() {
        val url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1"
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().execute("CREATE TABLE applicant_export_projection (applicant_id BIGINT PRIMARY KEY, account_id BIGINT NOT NULL, payload BLOB NOT NULL, event_version BIGINT NOT NULL, deleted BOOLEAN NOT NULL)")
        }
        val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantExportProjectionJpaRepository::class.java)) { _, method, args ->
            if (method.name == "findById") {
                return@newProxyInstance DriverManager.getConnection(url).use { connection ->
                    connection.prepareStatement("SELECT * FROM applicant_export_projection WHERE applicant_id=?").use { statement ->
                        statement.setObject(1, args[0])
                        statement.executeQuery().use { rows ->
                            java.util.Optional.ofNullable(if (rows.next()) hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity(
                                rows.getLong("applicant_id"), rows.getLong("account_id"), rows.getBytes("payload"), rows.getLong("event_version"), rows.getBoolean("deleted")) else null)
                        }
                    }
                }
            }
            val query = method.getAnnotation(Query::class.java).value
            val names = method.parameters.map { it.getAnnotation(org.springframework.data.repository.query.Param::class.java).value }
            val bindings = mutableListOf<Any?>()
            val sql = Regex(":([A-Za-z]+)").replace(query) { match ->
                bindings.add(args[names.indexOf(match.groupValues[1])]); "?"
            }
            DriverManager.getConnection(url).use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    bindings.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                    statement.executeUpdate()
                }
            }
        } as ApplicantExportProjectionJpaRepository
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val cipher = SnapshotCipher("test", mapOf("test" to key))
        val store = ApplicantProjectionStore(repository, cipher)
        fun form(version: Long) = ApplicationFormResponse.newBuilder().setApplicantId(1).setUserId(10)
            .setStatusVersion(version).setApplicantStatus(ApplicantStatus.APPLICANT_STATUS_SUBMITTED).setName("홍길동").build()
        fun event(version: Long, deleted: Boolean = false) = ApplicantStatusChangedEvent.newBuilder()
            .setApplicantId(1).setAccountId(10).setVersion(version).setApplicantDeleted(deleted)
            .setApplicantStatus(if (deleted) ApplicantStatus.APPLICANT_STATUS_NONE else ApplicantStatus.APPLICANT_STATUS_SUBMITTED)
            .also { if (!deleted) it.setEncryptedApplicationForm(ByteString.copyFrom(cipher.encrypt(form(version).toByteArray()))) }.build()
        fun row(): Triple<Long, Boolean, ByteArray> = DriverManager.getConnection(url).use { connection ->
            connection.createStatement().executeQuery("SELECT event_version, deleted, payload FROM applicant_export_projection WHERE applicant_id=1").use {
                assertTrue(it.next()); Triple(it.getLong(1), it.getBoolean(2), it.getBytes(3))
            }
        }
        // 기존 접수 원서는 status_version=0일 수 있다.
        store.save(form(0).toBuilder().setApplicantId(2).setUserId(20).build())
        store.apply(event(2)); store.apply(event(2)); store.apply(event(1))
        assertEquals(2L, row().first)
        assertEquals("홍길동", store.read(row().third).name)
        assertFalse(row().third.contentEquals(form(2).toByteArray()))
        val corrupted = row().third.clone().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val failure = assertThrows(hs.kr.entrydsm.admin.domain.exception.AdminDomainException::class.java) { store.read(corrupted) }
        assertEquals(hs.kr.entrydsm.admin.domain.enum.ErrorCode.APPLICATION_SNAPSHOT_INVALID, failure.errorCode)
        assertTrue(failure.cause is IllegalStateException)
        val invalidEnvelope = assertThrows(hs.kr.entrydsm.admin.domain.exception.AdminDomainException::class.java) { store.read(byteArrayOf()) }
        assertEquals(hs.kr.entrydsm.admin.domain.enum.ErrorCode.APPLICATION_SNAPSHOT_INVALID, invalidEnvelope.errorCode)
        store.apply(event(3, true)); store.save(form(2))
        assertEquals(3L, row().first); assertTrue(row().second); assertEquals(0, row().third.size)
        store.apply(event(4)); assertFalse(row().second)
        store.removeIfUnchanged(1, 3); assertFalse(row().second)
        store.removeIfUnchanged(1, 4); assertTrue(row().second)
        store.apply(event(4)); assertTrue(row().second)
        store.apply(event(5)); assertFalse(row().second)
        // 오래된 접수와 최신 삭제가 동시에 와도 최신 버전만 남는다.
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(executor.submit { store.apply(event(6)) }, executor.submit { store.apply(event(7, true)) })
            futures.forEach { it.get() }
        } finally { executor.shutdownNow() }
        assertEquals(7L, row().first); assertTrue(row().second)
    }
    @Test
    fun `실패 이벤트는 ACK하지 않고 pending 회수 성공 후 ACK한다`() {
        val calls = mutableListOf<String>()
        var fail = true
        val repository = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantExportProjectionJpaRepository::class.java)) { _, method, _ ->
            when (method.name) {
                "findById" -> java.util.Optional.empty<hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity>()
                "applyVersion" -> { calls.add("commit"); if (fail) throw IllegalStateException("DB unavailable"); 1 }
                else -> error(method.name)
            }
        } as ApplicantExportProjectionJpaRepository
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val event = ApplicantStatusChangedEvent.newBuilder().setApplicantId(1).setAccountId(10)
            .setVersion(3).setApplicantDeleted(true).build()
        var record = org.springframework.data.redis.connection.stream.StreamRecords.string(mapOf("payload" to Base64.getEncoder().encodeToString(event.toByteArray())))
            .withStreamKey("events").withId(org.springframework.data.redis.connection.stream.RecordId.of("1000-0"))
        val owner = org.springframework.data.redis.connection.stream.Consumer.from("admin-applicant-export", "old-process")
        var deliveryCount = 1L
        var fresh = false
        @Suppress("UNCHECKED_CAST")
        val operations = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(org.springframework.data.redis.core.StreamOperations::class.java)) { _, method, args ->
            when (method.name) {
                "createGroup" -> throw IllegalStateException("BUSYGROUP")
                "pending" -> org.springframework.data.redis.connection.stream.PendingMessages(owner.group, if (fresh) emptyList() else listOf(org.springframework.data.redis.connection.stream.PendingMessage(record.id, owner, java.time.Duration.ofMinutes(1), deliveryCount)))
                "claim" -> { calls.add("claim"); listOf(record) }
                "read" -> {
                    val offsets = args[2] as Array<org.springframework.data.redis.connection.stream.StreamOffset<String>>
                    if (fresh && offsets.single().offset.offset == ">") listOf(record) else emptyList<Any>()
                }
                "acknowledge" -> { calls.add("ack"); 1L }
                "add" -> { calls.add("quarantine"); org.springframework.data.redis.connection.stream.RecordId.of("2000-0") }
                "trim" -> { calls.add("trim"); 0L }
                else -> error(method.name)
            }
        } as org.springframework.data.redis.core.StreamOperations<String, String, String>
        val redis = object : org.springframework.data.redis.core.StringRedisTemplate() {
            @Suppress("UNCHECKED_CAST")
            override fun <HK : Any?, HV : Any?> opsForStream(): org.springframework.data.redis.core.StreamOperations<String, HK, HV> =
                operations as org.springframework.data.redis.core.StreamOperations<String, HK, HV>
        }
        val metrics = io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        val consumer = ApplicantExportEventConsumer(redis, ApplicantProjectionStore(repository, SnapshotCipher("test", mapOf("test" to key))), metrics, "events", "admin-1")
        consumer.receive()
        assertEquals(listOf("claim", "commit"), calls)
        fail = false; calls.clear(); consumer.receive()
        assertEquals(listOf("claim", "commit", "ack"), calls)
        assertEquals(1.0, metrics.counter("admin.applicant.projection.events", "result", "failure").count(), 0.0)
        assertEquals(1.0, metrics.counter("admin.applicant.projection.events", "result", "success").count(), 0.0)
        fail = true; deliveryCount = 5; calls.clear(); consumer.receive()
        assertEquals(listOf("claim", "commit", "quarantine", "ack", "trim"), calls)
        assertEquals(1.0, metrics.counter("admin.applicant.projection.events", "result", "quarantined").count(), 0.0)
        // 새로 읽은 결정적 오류는 pending 재전달 없이 즉시 격리한다.
        fresh = true
        val invalidPayloads = listOf(
            emptyMap<String, String>(),
            mapOf("payload" to "%%%"),
            mapOf("payload" to Base64.getEncoder().encodeToString(byteArrayOf(0x80.toByte()))),
            mapOf("payload" to Base64.getEncoder().encodeToString(event.toBuilder().setAccountId(0).build().toByteArray())),
        )
        invalidPayloads.forEachIndexed { index, payload ->
            record = org.springframework.data.redis.connection.stream.StreamRecords.string(payload)
                .withStreamKey("events").withId(org.springframework.data.redis.connection.stream.RecordId.of("1001-$index"))
            calls.clear()
            consumer.receive()
            assertEquals(listOf("quarantine", "ack", "trim"), calls)
        }
        assertEquals(6.0, metrics.counter("admin.applicant.projection.events", "result", "failure").count(), 0.0)
        assertEquals(5.0, metrics.counter("admin.applicant.projection.events", "result", "quarantined").count(), 0.0)
    }

}
