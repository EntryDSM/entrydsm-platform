package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.PassResultPort
import hs.kr.entrydsm.application.application.port.`in`.command.AnnouncePassResultsCommand
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.model.PassResult
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsRequest
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsResponse
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.PassResultEntry
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.application.grpc.ResultType as GrpcResultType
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.Instant
import java.time.ZoneOffset
import org.springframework.stereotype.Component

@Component
class ApplicationGrpcService(
    private val applicationPort: ApplicationPort,
    private val passResultPort: PassResultPort,
) : ApplicationServiceGrpc.ApplicationServiceImplBase() {
    override fun createApplication(
        request: CreateApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.findByUserId(request.userId)
            ?: applicationPort.createApplicant(CreateApplicantCommand(request.userId)).snapshot
    }

    override fun getApplication(
        request: GetApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.findByUserId(request.userId)
            ?: throw ApplicantNotFoundException(request.userId)
    }

    override fun cancelApplication(
        request: CancelApplicationRequest,
        responseObserver: StreamObserver<ApplicationResponse>,
    ) = responseObserver.respond {
        request.userId.validate()
        applicationPort.cancel(request.userId, request.reason.takeIf { request.hasReason() })
    }

    override fun announcePassResults(
        request: AnnouncePassResultsRequest,
        responseObserver: StreamObserver<AnnouncePassResultsResponse>,
    ) {
        try {
            val applied = passResultPort.announce(
                AnnouncePassResultsCommand(request.entriesList.map { it.toPassResult() }),
            )
            responseObserver.onNext(
                AnnouncePassResultsResponse.newBuilder().setAppliedCount(applied).build(),
            )
            responseObserver.onCompleted()
        } catch (exception: Exception) {
            responseObserver.onError(exception.toStatus().withCause(exception).asRuntimeException())
        }
    }

    private fun PassResultEntry.toPassResult(): PassResult = PassResult(
        applicantId = applicantId,
        resultType = when (resultType) {
            GrpcResultType.RESULT_TYPE_DOCUMENT -> ResultType.DOCUMENT
            GrpcResultType.RESULT_TYPE_FINAL -> ResultType.FINAL
            else -> throw IllegalArgumentException("result_type must be DOCUMENT or FINAL")
        },
        result = when (result) {
            GrpcPassStatus.PASS_STATUS_PASSED -> PassResultStatus.PASS
            GrpcPassStatus.PASS_STATUS_FAILED -> PassResultStatus.FAIL
            else -> throw IllegalArgumentException("result must be PASSED or FAILED")
        },
        processedBy = processedBy.takeIf { hasProcessedBy() },
        processedAt = Instant.ofEpochMilli(processedAtEpochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDateTime(),
    )

    private fun Long.validate() {
        require(this > 0) { "user_id must be positive" }
    }

    private fun StreamObserver<ApplicationResponse>.respond(block: () -> ApplicationSnapshotResult) {
        try {
            onNext(block().toResponse())
            onCompleted()
        } catch (exception: Exception) {
            onError(exception.toStatus().withCause(exception).asRuntimeException())
        }
    }

    private fun Exception.toStatus(): Status = when (this) {
        is IllegalArgumentException -> Status.INVALID_ARGUMENT
        is ApplicantNotFoundException -> Status.NOT_FOUND
        is ApplicationCancelNotAllowedException -> Status.FAILED_PRECONDITION
        else -> Status.INTERNAL
    }

    private fun ApplicationSnapshotResult.toResponse(): ApplicationResponse =
        ApplicationResponse.newBuilder()
            .setUserId(userId)
            .setApplicantStatus(
                when (applicantStatus) {
                    ApplicantStatus.DRAFT -> GrpcApplicantStatus.APPLICANT_STATUS_DRAFT
                    ApplicantStatus.SUBMITTED -> GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED
                    ApplicantStatus.REVIEWING -> GrpcApplicantStatus.APPLICANT_STATUS_REVIEWING
                    ApplicantStatus.COMPLETED -> GrpcApplicantStatus.APPLICANT_STATUS_COMPLETED
                    ApplicantStatus.CANCELED -> GrpcApplicantStatus.APPLICANT_STATUS_CANCELED
                },
            )
            .apply {
                submittedAt?.let { setSubmittedAtEpochMillis(it.toInstant(ZoneOffset.UTC).toEpochMilli()) }
                announcedAt?.let { setAnnouncedAtEpochMillis(it.toInstant(ZoneOffset.UTC).toEpochMilli()) }
            }
            .setUpdatedAtEpochMillis(updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli())
            .setPassStatus(
                when (passStatus) {
                    PassResultStatus.PENDING -> GrpcPassStatus.PASS_STATUS_NOT_ANNOUNCED
                    PassResultStatus.PASS -> GrpcPassStatus.PASS_STATUS_PASSED
                    PassResultStatus.FAIL -> GrpcPassStatus.PASS_STATUS_FAILED
                },
            )
            .build()
}
