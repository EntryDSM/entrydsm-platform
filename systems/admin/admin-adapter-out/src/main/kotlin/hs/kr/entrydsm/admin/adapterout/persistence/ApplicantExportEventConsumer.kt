package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportEventJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportEventJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportProjectionJpaRepository
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import java.time.Duration
import java.util.Base64
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ApplicantExportEventConsumer(
    private val redis: StringRedisTemplate,
    private val repository: ApplicantExportEventJpaRepository,
    private val projectionRepository: ApplicantExportProjectionJpaRepository,
    @Value("\${entrydsm.application.events.applicant-status-stream:application.applicant-status}")
    private val stream: String,
) {
    private val group = "admin-applicant-export"
    private val consumer = UUID.randomUUID().toString()

    fun createGroup() {
        runCatching { redis.opsForStream<String, String>().createGroup(stream, ReadOffset.from("0-0"), group) }
    }

    @Scheduled(fixedDelayString = "\${admin.export-event.poll-delay-ms:1000}")
    fun receive() {
        createGroup()
        val records = runCatching {
            redis.opsForStream<String, String>().read(
                Consumer.from(group, consumer),
                StreamReadOptions.empty().count(100).block(Duration.ofMillis(100)),
                StreamOffset.create(stream, ReadOffset.lastConsumed()),
            )
        }.getOrNull().orEmpty()

        records.forEach { record ->
            val payload = record.value["payload"] ?: return@forEach
            val event = ApplicantStatusChangedEvent.parseFrom(Base64.getDecoder().decode(payload))
            if (event.applicantId <= 0) {
                redis.opsForStream<String, String>().acknowledge(stream, group, record.id)
                return@forEach
            }
            if ((repository.findTopByApplicantIdOrderByEventVersionDesc(event.applicantId)?.eventVersion ?: 0) >= event.version) {
                redis.opsForStream<String, String>().acknowledge(stream, group, record.id)
                return@forEach
            }
            repository.save(
                ApplicantExportEventJpaEntity(
                    eventId = event.eventId,
                    applicantId = event.applicantId,
                    accountId = event.accountId,
                    applicantStatus = event.applicantStatus.name,
                    eventVersion = event.version,
                ),
            )
            if (event.applicantDeleted) projectionRepository.deleteById(event.applicantId)
            redis.opsForStream<String, String>().acknowledge(stream, group, record.id)
        }
    }
}
