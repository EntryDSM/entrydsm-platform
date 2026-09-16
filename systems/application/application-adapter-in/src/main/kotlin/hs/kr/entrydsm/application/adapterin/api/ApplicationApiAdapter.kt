package hs.kr.entrydsm.application.adapterin.api

import hs.kr.entrydsm.application.api.ApplicationApi
import hs.kr.entrydsm.application.api.ApplicationApiException
import hs.kr.entrydsm.application.api.ApplicationStatusView
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.stereotype.Component
import hs.kr.entrydsm.application.api.ApplicantStatus as ApiApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus as ApiPassStatus

/**
 * 다른 모듈의 원서 상태 요청을 use case 로 넘긴다. gRPC 서버(`ApplicationGrpcService`)가 하던 검증·변환을 그대로 한다.
 *
 * 내부 예외는 [ApplicationApiException] 으로만 바꿔 내보낸다. 호출 모듈이 application 의 예외 타입을 알 필요가 없다.
 */
@Component
class ApplicationApiAdapter(
    private val applicationPort: ApplicationPort,
) : ApplicationApi {
    override fun createApplication(userId: Long): ApplicationStatusView = translate(userId) {
        applicationPort.findByUserId(userId)
            ?: applicationPort.createApplicant(CreateApplicantCommand(userId)).snapshot
    }

    override fun getApplication(userId: Long): ApplicationStatusView = translate(userId) {
        applicationPort.findByUserId(userId) ?: throw ApplicantNotFoundException(userId)
    }

    override fun cancelApplication(userId: Long, reason: String?): ApplicationStatusView = translate(userId) {
        applicationPort.cancel(userId, reason)
    }

    private fun translate(userId: Long, block: () -> ApplicationSnapshotResult): ApplicationStatusView {
        if (userId <= 0) {
            throw ApplicationApiException.InvalidArgument("user_id must be positive")
        }
        return try {
            block().toView()
        } catch (exception: IllegalArgumentException) {
            throw ApplicationApiException.InvalidArgument(exception.message ?: "invalid argument", exception)
        } catch (exception: ApplicantNotFoundException) {
            throw ApplicationApiException.NotFound(userId, exception)
        } catch (exception: ApplicationCancelNotAllowedException) {
            throw ApplicationApiException.CancelNotAllowed(userId, exception)
        }
    }

    private fun ApplicationSnapshotResult.toView() = ApplicationStatusView(
        userId = userId,
        applicantStatus = when (applicantStatus) {
            ApplicantStatus.DRAFT -> ApiApplicantStatus.DRAFT
            ApplicantStatus.SUBMITTED -> ApiApplicantStatus.SUBMITTED
            ApplicantStatus.REVIEWING -> ApiApplicantStatus.REVIEWING
            ApplicantStatus.COMPLETED -> ApiApplicantStatus.COMPLETED
            ApplicantStatus.CANCELED -> ApiApplicantStatus.CANCELED
        },
        submittedAt = submittedAt?.toUtcInstant(),
        updatedAt = updatedAt.toUtcInstant(),
        passStatus = when (passStatus) {
            PassResultStatus.PENDING -> ApiPassStatus.NOT_ANNOUNCED
            PassResultStatus.PASS -> ApiPassStatus.PASSED
            PassResultStatus.FAIL -> ApiPassStatus.FAILED
        },
        announcedAt = announcedAt?.toUtcInstant(),
    )

    /** 저장된 시각은 UTC 로 간주한다. gRPC 계약에서 쓰던 규칙과 같다. */
    private fun LocalDateTime.toUtcInstant() = toInstant(ZoneOffset.UTC)
}
