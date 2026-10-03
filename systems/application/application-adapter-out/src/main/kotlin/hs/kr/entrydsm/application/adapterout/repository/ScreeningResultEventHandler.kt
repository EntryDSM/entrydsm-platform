package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.PassResultId
import hs.kr.entrydsm.application.adapterout.entity.PassResultJpaEntity
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.nowUtc
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.application.grpc.ScreeningResultChangedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Component
class ScreeningResultEventHandler(
    private val repository: ApplicantJpaRepository,
    private val outbox: ApplicantStatusEventOutbox,
    private val applicationPort: ApplicationPort,
) {
    @Transactional
    fun consume(event: ScreeningResultChangedEvent) {
        require(event.applicantId > 0 && event.version > 0)
        val type = when (event.passStatus) {
            PassStatus.PASS_STATUS_FIRST_PASSED, PassStatus.PASS_STATUS_FIRST_FAILED -> ResultType.DOCUMENT
            PassStatus.PASS_STATUS_FINAL_PASSED, PassStatus.PASS_STATUS_FINAL_FAILED -> ResultType.FINAL
            PassStatus.PASS_STATUS_NOT_ANNOUNCED -> null
            else -> error("지원하지 않는 합격 상태: ${event.passStatus}")
        }
        val applicant = repository.findForUpdate(event.applicantId) ?: return
        if (event.version <= applicant.screeningResultVersion) return
        applicant.screeningResultVersion = event.version
        // 취소·미제출 원서에 지연된 관리자 결과를 다시 붙이지 않는다.
        if (applicant.status in setOf(ApplicantStatus.DRAFT, ApplicantStatus.CANCELED)) return

        val processedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.occurredAtEpochMillis), ZoneOffset.UTC)
        // 1차 재산출·강제 대기 전환은 이전 최종 결과도 철회한다.
        if (type != ResultType.FINAL) {
            applicant.passResults.removeAll { type == null || it.id.resultType == ResultType.FINAL }
        }
        if (type != null) {
            val result = applicant.passResults.firstOrNull { it.id.resultType == type }
                ?: PassResultJpaEntity(PassResultId(applicant.id, type), applicant).also { applicant.passResults.add(it) }
            result.result = when (event.passStatus) {
                PassStatus.PASS_STATUS_FIRST_PASSED, PassStatus.PASS_STATUS_FINAL_PASSED -> PassResultStatus.PASS
                else -> PassResultStatus.FAIL
            }
            result.processedAt = processedAt
        }
        applicant.statusVersion++
        applicant.updatedAt = nowUtc()
        val snapshot = applicant.toDomain()
        outbox.add(ApplicantStatusChanged(
            accountId = snapshot.accountId, applicantId = snapshot.id, status = snapshot.status,
            occurredAt = snapshot.updatedAt, version = snapshot.statusVersion, submittedAt = snapshot.submittedAt,
            passStatus = snapshot.passStatus, announcedAt = snapshot.announcedAt, passResultType = snapshot.passResultType,
            applicationForm = requireNotNull(applicationPort.findApplicationForm(snapshot.accountId)),
        ))
    }
}
