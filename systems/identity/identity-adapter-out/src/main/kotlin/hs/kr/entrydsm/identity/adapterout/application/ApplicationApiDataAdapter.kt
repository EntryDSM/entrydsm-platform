package hs.kr.entrydsm.identity.adapterout.application

import hs.kr.entrydsm.application.api.ApplicationApi
import hs.kr.entrydsm.application.api.ApplicationApiException
import hs.kr.entrydsm.application.api.ApplicationStatusView
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import hs.kr.entrydsm.application.api.ApplicantStatus as ApiApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus as ApiPassStatus

/**
 * application 모듈의 원서 상태 API 를 같은 프로세스 안에서 부른다. gRPC 클라이언트(`GrpcApplicationDataAdapter`)를 대신한다.
 *
 * 호출은 identity 의 트랜잭션에 합류한다. application 이 던진 예외는 삼키지 않고 identity 오류 코드로 바꿔 다시 던진다.
 * 삼키면 합류한 트랜잭션이 롤백 전용으로 표시된 채 커밋을 시도하게 된다.
 */
@Component
@Profile("prod", "dev", "integration")
class ApplicationApiDataAdapter(
    private val applicationApi: ApplicationApi,
) : ApplicationDataPort {
    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        call { applicationApi.createApplication(userId) }.toSnapshot()

    /** 원서가 없으면 null 이다. gRPC 에서 NOT_FOUND 를 null 로 받던 것과 같다. */
    override fun findByUserId(userId: Long): ApplicationSnapshot? = call {
        try {
            applicationApi.getApplication(userId)
        } catch (@Suppress("SwallowedException") exception: ApplicationApiException.NotFound) {
            null
        }
    }?.toSnapshot()

    override fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot =
        call { applicationApi.cancelApplication(userId, reason) }.toSnapshot()

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (exception: RuntimeException) {
            throw IdentityDomainException(exception.toErrorCode(), exception)
        }

    private fun RuntimeException.toErrorCode(): ErrorCode = when (this) {
        is ApplicationApiException.InvalidArgument -> ErrorCode.INVALID_REQUEST_BODY
        is ApplicationApiException.NotFound -> ErrorCode.USER_NOT_FOUND
        is ApplicationApiException.CancelNotAllowed -> ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED
        else -> ErrorCode.INTERNAL_SERVER_ERROR
    }

    private fun ApplicationStatusView.toSnapshot(): ApplicationSnapshot = ApplicationSnapshot(
        userId = userId,
        applicantStatus = applicantStatus.toIdentityApplicantStatus(),
        submittedAt = submittedAt,
        updatedAt = updatedAt,
        passStatus = passStatus.toIdentityPassStatus(),
        announcedAt = announcedAt,
    )
}

internal fun ApiApplicantStatus.toIdentityApplicantStatus(): ApplicantStatus = when (this) {
    ApiApplicantStatus.DRAFT -> ApplicantStatus.DRAFT
    ApiApplicantStatus.SUBMITTED -> ApplicantStatus.SUBMITTED
    ApiApplicantStatus.REVIEWING -> ApplicantStatus.REVIEWING
    ApiApplicantStatus.COMPLETED -> ApplicantStatus.COMPLETED
    ApiApplicantStatus.CANCELED -> ApplicantStatus.CANCELED
}

internal fun ApiPassStatus.toIdentityPassStatus(): PassStatus = when (this) {
    ApiPassStatus.NOT_ANNOUNCED -> PassStatus.NOT_ANNOUNCED
    ApiPassStatus.PASSED -> PassStatus.PASSED
    ApiPassStatus.FAILED -> PassStatus.FAILED
}
