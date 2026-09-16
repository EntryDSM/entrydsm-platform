package hs.kr.entrydsm.application.api

import java.time.Instant

/**
 * application 모듈이 다른 모듈에 공개하는 원서 상태 API다. `application.proto`의 `ApplicationService`를 대신한다.
 *
 * 같은 프로세스 안에서 호출되므로 호출자의 트랜잭션에 합류한다. 예외는 [ApplicationApiException] 으로만 알린다.
 * 그 밖의 예외(저장소 장애 등)는 변환하지 않고 그대로 전파한다.
 */
interface ApplicationApi {
    /** 원서가 없으면 만들고, 이미 있으면 있는 원서를 돌려준다. */
    fun createApplication(userId: Long): ApplicationStatusView

    /** @throws ApplicationApiException.NotFound 원서가 없을 때 */
    fun getApplication(userId: Long): ApplicationStatusView

    /**
     * 제출한 원서를 취소한다.
     *
     * @param reason 취소 사유. null 이거나 비어 있으면 사유 없이 취소한다
     * @throws ApplicationApiException.NotFound 원서가 없을 때
     * @throws ApplicationApiException.CancelNotAllowed 제출 상태가 아닐 때
     */
    fun cancelApplication(userId: Long, reason: String?): ApplicationStatusView
}

data class ApplicationStatusView(
    val userId: Long,
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val updatedAt: Instant,
    val passStatus: PassStatus,
    val announcedAt: Instant?,
)

enum class ApplicantStatus {
    DRAFT,
    SUBMITTED,
    REVIEWING,
    COMPLETED,
    CANCELED,
}

enum class PassStatus {
    NOT_ANNOUNCED,
    PASSED,
    FAILED,
}

/** gRPC 상태 코드(INVALID_ARGUMENT, NOT_FOUND, FAILED_PRECONDITION)를 대신한다. */
sealed class ApplicationApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class InvalidArgument(message: String, cause: Throwable? = null) : ApplicationApiException(message, cause)

    class NotFound(val userId: Long, cause: Throwable? = null) :
        ApplicationApiException("application not found: userId=$userId", cause)

    class CancelNotAllowed(val userId: Long, cause: Throwable? = null) :
        ApplicationApiException("only submitted applications can be canceled: userId=$userId", cause)
}
