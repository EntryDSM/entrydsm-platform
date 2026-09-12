package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.AnnouncedPassResult
import hs.kr.entrydsm.admin.domain.port.out.PassResultAnnouncementPort
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsRequest
import hs.kr.entrydsm.application.grpc.ApplicantSummary
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.PassResultEntry
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.ResultType as GrpcResultType
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 원서 원본을 application 시스템에서 읽고, 산출한 전형 결과를 그쪽에 넘깁니다.
 *
 * admin 이 원서를 자기 DB 에 복제해 두면 실제로 접수된 원서와 어긋납니다. 원서의
 * 소유자는 application 이므로 조회는 여기를 거칩니다. 전형 결과도 마찬가지로,
 * admin DB 에만 적으면 수험생이 보는 합격 조회에 나타나지 않습니다.
 */
@Component
class GrpcApplicantDataAdapter(
    @Value("\${application.grpc.host}") host: String,
    @Value("\${application.grpc.port}") port: Int,
    @Value("\${application.grpc.deadline-ms:5000}") private val deadlineMs: Long,
) : PassResultAnnouncementPort, DisposableBean {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = ApplicationServiceGrpc.newBlockingStub(channel)

    /** 제출된 원서 전체입니다. 전형 정보는 비어 있으므로 호출자가 채웁니다. */
    fun findAllSubmitted(): List<ApplicantRecord> =
        try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .listApplicants(ListApplicantsRequest.getDefaultInstance())
                .itemsList
                .map { it.toRecord() }
        } catch (exception: StatusRuntimeException) {
            throw exception.toDomainException(ErrorCode.INTERNAL_SERVER_ERROR)
        }

    override fun announce(results: List<AnnouncedPassResult>, processedAt: Instant): Int {
        if (results.isEmpty()) return 0

        val entries = results.mapNotNull { it.toEntry(processedAt) }
        if (entries.isEmpty()) return 0

        return try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .announcePassResults(
                    AnnouncePassResultsRequest.newBuilder().addAllEntries(entries).build(),
                )
                .appliedCount
        } catch (exception: StatusRuntimeException) {
            throw exception.toDomainException(ErrorCode.PASS_RESULT_ANNOUNCEMENT_FAILED)
        }
    }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    /** 아직 산출 전(PENDING)인 상태는 발표가 아니므로 보내지 않습니다. */
    private fun AnnouncedPassResult.toEntry(processedAt: Instant): PassResultEntry? {
        val resultType = when (status) {
            ApplicantStatus.FIRST_PASS, ApplicantStatus.FIRST_FAIL -> GrpcResultType.RESULT_TYPE_DOCUMENT
            ApplicantStatus.FINAL_PASS, ApplicantStatus.FINAL_FAIL -> GrpcResultType.RESULT_TYPE_FINAL
            ApplicantStatus.PENDING -> return null
        }
        val result = when (status) {
            ApplicantStatus.FIRST_PASS, ApplicantStatus.FINAL_PASS -> GrpcPassStatus.PASS_STATUS_PASSED
            else -> GrpcPassStatus.PASS_STATUS_FAILED
        }

        return PassResultEntry.newBuilder()
            .setApplicantId(applicantId)
            .setResultType(resultType)
            .setResult(result)
            .setProcessedAtEpochMillis(processedAt.toEpochMilli())
            .build()
    }

    private fun ApplicantSummary.toRecord(): ApplicantRecord = ApplicantRecord(
        applicantId = applicantId,
        name = name,
        birthDate = if (hasBirthDate()) LocalDate.parse(birthDate) else null,
        phoneNumber = phoneNumber,
        region = when (region) {
            GrpcRegion.REGION_DAEJEON -> Region.DAEJEON
            GrpcRegion.REGION_NATIONAL -> Region.NATIONWIDE
            else -> null
        },
        admissionType = when (admissionType) {
            GrpcAdmissionType.ADMISSION_TYPE_REGULAR -> AdmissionType.GENERAL
            GrpcAdmissionType.ADMISSION_TYPE_MEISTER -> AdmissionType.MEISTER
            GrpcAdmissionType.ADMISSION_TYPE_SOCIAL -> AdmissionType.SOCIAL
            else -> null
        },
        graduationStatus = when (graduationType) {
            GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE -> GraduationStatus.EXPECTED
            GrpcGraduationType.GRADUATION_TYPE_GRADUATED -> GraduationStatus.GRADUATED
            GrpcGraduationType.GRADUATION_TYPE_GED -> GraduationStatus.GED
            else -> null
        },
        schoolName = schoolName,
        submittedAt = if (hasSubmittedAtEpochMillis()) Instant.ofEpochMilli(submittedAtEpochMillis) else null,
        score = if (hasScore()) {
            ApplicantScoreRecord(
                subjectScore = score.subjectScore,
                attendanceScore = score.attendanceScore,
                volunteerScore = score.volunteerScore,
                totalScore = score.totalScore,
            )
        } else {
            null
        },
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )

    private fun StatusRuntimeException.toDomainException(fallback: ErrorCode): AdminDomainException =
        AdminDomainException(
            when (status.code) {
                Status.Code.INVALID_ARGUMENT -> ErrorCode.INVALID_REQUEST_BODY
                Status.Code.NOT_FOUND -> ErrorCode.APPLICANT_NOT_FOUND
                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED ->
                    ErrorCode.APPLICATION_SERVICE_UNAVAILABLE
                else -> fallback
            },
            this,
        )
}

/** application 이 소유한 원서 본문입니다. */
data class ApplicantRecord(
    val applicantId: Long,
    val name: String,
    val birthDate: LocalDate?,
    val phoneNumber: String,
    val region: Region?,
    val admissionType: AdmissionType?,
    val graduationStatus: GraduationStatus?,
    val schoolName: String,
    val submittedAt: Instant?,
    val score: ApplicantScoreRecord?,
    val updatedAt: Instant,
)

data class ApplicantScoreRecord(
    val subjectScore: Double,
    val attendanceScore: Double,
    val volunteerScore: Double,
    val totalScore: Double,
)
