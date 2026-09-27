package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.domain.nowUtc
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.Base64

@Component
class ApplicantStatusOutboxRelay(
    private val repository: ApplicantStatusOutboxJpaRepository,
    private val redis: StringRedisTemplate,
    @Value("\${entrydsm.application.events.applicant-status-stream:application.applicant-status}")
    private val stream: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${entrydsm.application.events.relay-delay-ms:1000}")
    @Transactional
    fun relay() {
        repository.findUnpublishedForUpdate().forEach { event ->
            try {
                redis.opsForStream<String, String>().add(
                    stream,
                    mapOf("eventId" to event.eventId, "payload" to Base64.getEncoder().encodeToString(event.payload)),
                )
                event.publishedAt = nowUtc()
            } catch (exception: Exception) {
                logger.error("Failed to publish applicant status event [eventId={}]", event.eventId, exception)
            }
        }
    }
}
