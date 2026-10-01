package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.repository.ScreeningResultOutboxJpaRepository
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.application.grpc.ScreeningResultChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64

@Component
class ScreeningResultOutboxRelay(
    private val repository: ScreeningResultOutboxJpaRepository,
    private val redis: StringRedisTemplate,
    @Value("\${admin.events.screening-result-stream:admin.screening-result}") private val stream: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${admin.events.relay-delay-ms:1000}")
    @Transactional
    fun relay() {
        repository.findUnpublishedForUpdate().forEach { row ->
            try {
                val event = ScreeningResultChangedEvent.newBuilder()
                    .setApplicantId(row.applicantId)
                    .setVersion(requireNotNull(row.id))
                    .setPassStatus(when (row.status) {
                        ApplicantStatus.PENDING -> PassStatus.PASS_STATUS_NOT_ANNOUNCED
                        ApplicantStatus.FIRST_PASS -> PassStatus.PASS_STATUS_FIRST_PASSED
                        ApplicantStatus.FIRST_FAIL -> PassStatus.PASS_STATUS_FIRST_FAILED
                        ApplicantStatus.FINAL_PASS -> PassStatus.PASS_STATUS_FINAL_PASSED
                        ApplicantStatus.FINAL_FAIL -> PassStatus.PASS_STATUS_FINAL_FAILED
                    })
                    .setOccurredAtEpochMillis(row.createdAt.toEpochMilli())
                    .build()
                checkNotNull(redis.opsForStream<String, String>().add(
                    stream, mapOf("payload" to Base64.getEncoder().encodeToString(event.toByteArray())),
                ))
                row.publishedAt = Instant.now()
            } catch (exception: Exception) {
                logger.error("합격 결과 이벤트 전송 실패 [eventId={}]", row.id, exception)
            }
        }
    }
}
