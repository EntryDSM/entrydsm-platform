package hs.kr.entrydsm.identity.adapterout.grpc

import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("prod", "dev", "integration")
class GrpcApplicationDataAdapter(
    @Value("\${application.grpc.host}") host: String,
    @Value("\${application.grpc.port}") port: Int,
    @Value("\${application.grpc.deadline-ms:3000}") private val deadlineMs: Long,
) : ApplicationDataPort, DisposableBean {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = ApplicationServiceGrpc.newBlockingStub(channel)

    override fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot =
        call {
            createApplication(
                CreateApplicationRequest.newBuilder()
                    .setUserId(userId)
                    .build(),
            )
        }.toSnapshot()

    override fun findByUserId(userId: Long): ApplicationSnapshot? = try {
        stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
            .getApplication(
                GetApplicationRequest.newBuilder()
                    .setUserId(userId)
                    .build(),
            )
            .toSnapshot()
    } catch (exception: StatusRuntimeException) {
        if (exception.status.code == Status.Code.NOT_FOUND) null else throw exception.toDomainException()
    }

    override fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot =
        call {
            cancelApplication(
                CancelApplicationRequest.newBuilder()
                    .setUserId(userId)
                    .apply { reason?.let(::setReason) }
                    .build(),
            )
        }.toSnapshot()

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun call(block: ApplicationServiceGrpc.ApplicationServiceBlockingStub.() -> ApplicationResponse): ApplicationResponse =
        try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).block()
        } catch (exception: StatusRuntimeException) {
            throw exception.toDomainException()
        }

    private fun ApplicationResponse.toSnapshot(): ApplicationSnapshot = ApplicationSnapshot(
        userId = userId,
        applicantStatus = when (applicantStatus) {
            GrpcApplicantStatus.APPLICANT_STATUS_NONE -> ApplicantStatus.NONE
            GrpcApplicantStatus.APPLICANT_STATUS_DRAFT -> ApplicantStatus.DRAFT
            GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED -> ApplicantStatus.SUBMITTED
            GrpcApplicantStatus.APPLICANT_STATUS_REVIEWING -> ApplicantStatus.REVIEWING
            GrpcApplicantStatus.APPLICANT_STATUS_COMPLETED -> ApplicantStatus.COMPLETED
            GrpcApplicantStatus.APPLICANT_STATUS_CANCELED -> ApplicantStatus.CANCELED
            else -> throw IdentityDomainException(ErrorCode.INTERNAL_SERVER_ERROR)
        },
        submittedAt = submittedAtEpochMillis.takeIf { hasSubmittedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
        passStatus = when (passStatus) {
            GrpcPassStatus.PASS_STATUS_NOT_ANNOUNCED -> PassStatus.NOT_ANNOUNCED
            GrpcPassStatus.PASS_STATUS_PASSED -> PassStatus.PASSED
            GrpcPassStatus.PASS_STATUS_FAILED -> PassStatus.FAILED
            else -> throw IdentityDomainException(ErrorCode.INTERNAL_SERVER_ERROR)
        },
        announcedAt = announcedAtEpochMillis.takeIf { hasAnnouncedAtEpochMillis() }?.let(Instant::ofEpochMilli),
    )

    private fun StatusRuntimeException.toDomainException(): IdentityDomainException = IdentityDomainException(
        when (status.code) {
            Status.Code.INVALID_ARGUMENT -> ErrorCode.INVALID_REQUEST_BODY
            Status.Code.NOT_FOUND -> ErrorCode.USER_NOT_FOUND
            Status.Code.FAILED_PRECONDITION -> ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> ErrorCode.APPLICATION_SERVICE_UNAVAILABLE
            else -> ErrorCode.INTERNAL_SERVER_ERROR
        },
        this,
    )
}
