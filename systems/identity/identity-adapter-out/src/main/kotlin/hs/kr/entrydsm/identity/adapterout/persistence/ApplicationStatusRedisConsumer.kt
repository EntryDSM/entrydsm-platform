package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import io.lettuce.core.RedisBusyException
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Base64

@Component
class ApplicationStatusRedisConsumer(
    private val redis: StringRedisTemplate,
    private val eventConsumer: ApplicationEventConsumer,
    @Value("\${application.events.applicant-status-stream:application.applicant-status}") private val stream: String,
    @Value("\${application.events.consumer-group:identity}") private val group: String,
    @Value("\${application.events.consumer-name:identity}") private val consumerName: String,
) {
    @Scheduled(fixedDelayString = "\${application.events.poll-delay-ms:1000}")
    fun poll() {
        ensureGroup()
        read(ReadOffset.from("0"))
        read(ReadOffset.lastConsumed())
    }

    private fun ensureGroup() {
        try {
            redis.opsForStream<String, String>().createGroup(stream, ReadOffset.from("0"), group)
        } catch (exception: RedisSystemException) {
            if (exception.cause !is RedisBusyException) throw exception
        }
    }

    private fun read(offset: ReadOffset) {
        redis.opsForStream<String, String>().read(
            Consumer.from(group, consumerName),
            StreamReadOptions.empty().count(100),
            StreamOffset.create(stream, offset),
        ).orEmpty().forEach { record ->
            val event = ApplicantStatusChangedEvent.parseFrom(Base64.getDecoder().decode(record.value.getValue("payload")))
            eventConsumer.consume(event.toDomain())
            redis.opsForStream<String, String>().acknowledge(stream, group, record.id)
        }
    }

    private fun ApplicantStatusChangedEvent.toDomain() = ApplicationStateChangedEvent(
        eventId = eventId,
        userId = accountId,
        version = version,
        applicantStatus = ApplicantStatus.valueOf(applicantStatus.name.removePrefix("APPLICANT_STATUS_")),
        submittedAt = submittedAtEpochMillis.takeIf { hasSubmittedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        passStatus = PassStatus.valueOf(passStatus.name.removePrefix("PASS_STATUS_")),
        announcedAt = announcedAtEpochMillis.takeIf { hasAnnouncedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    )
}
