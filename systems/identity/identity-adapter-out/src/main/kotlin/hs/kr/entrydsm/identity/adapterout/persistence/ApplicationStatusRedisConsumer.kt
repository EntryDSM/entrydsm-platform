package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import hs.kr.entrydsm.identity.application.port.out.ApplicationEventConsumer
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import io.lettuce.core.RedisBusyException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import java.time.Instant
import java.util.Base64

@Component
@Profile("prod", "dev", "integration")
class ApplicationStatusRedisConsumer(
    private val redis: StringRedisTemplate,
    private val eventConsumer: ApplicationEventConsumer,
    @Value("\${application.events.applicant-status-stream:application.applicant-status}") private val stream: String,
    @Value("\${application.events.consumer-group:identity}") private val group: String,
    @Value("\${application.events.consumer-name:identity}") private val consumerName: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
            // 한 이벤트가 실패해도 뒤 이벤트는 처리한다. 실패한 이벤트는 ack 하지 않아 다음 poll 에 다시 시도하고,
            // 그사이 같은 지원자의 더 새 이벤트가 먼저 반영됐으면 version 검사로 버려진다.
            // ponytail: 끝내 실패하는 이벤트는 poll 마다 다시 시도하며 ERROR 를 남긴다. 쌓이면 전달 횟수 상한과 dead-letter 를 둔다.
            try {
                val event = ApplicantStatusChangedEvent.parseFrom(Base64.getDecoder().decode(record.value.getValue("payload")))
                eventConsumer.consume(event.toDomain())
                redis.opsForStream<String, String>().acknowledge(stream, group, record.id)
            } catch (exception: Exception) {
                logger.error("Failed to consume applicant status event [recordId={}]", record.id, exception)
            }
        }
    }

    private fun ApplicantStatusChangedEvent.toDomain() = ApplicationStateChangedEvent(
        eventId = eventId,
        userId = accountId,
        applicantId = applicantId,
        deleted = applicantDeleted,
        version = version,
        applicantStatus = ApplicantStatus.valueOf(applicantStatus.name.removePrefix("APPLICANT_STATUS_")),
        submittedAt = submittedAtEpochMillis.takeIf { hasSubmittedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        passStatus = when (passStatus.name.removePrefix("PASS_STATUS_")) {
            "PASSED" -> PassStatus.FIRST_PASSED
            "FAILED" -> PassStatus.FIRST_FAILED
            else -> PassStatus.valueOf(passStatus.name.removePrefix("PASS_STATUS_"))
        },
        announcedAt = announcedAtEpochMillis.takeIf { hasAnnouncedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis),
    )
}
