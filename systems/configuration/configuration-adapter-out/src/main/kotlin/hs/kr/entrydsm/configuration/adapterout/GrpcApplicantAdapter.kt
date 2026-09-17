package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicantPort
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GrpcApplicantAdapter(
    @Value("\${application.grpc.host}") host: String,
    @Value("\${application.grpc.port}") port: Int,
    @Value("\${application.grpc.deadline-ms:3000}") private val deadlineMs: Long,
) : ApplicantPort, DisposableBean {

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = ApplicationServiceGrpc.newBlockingStub(channel)

    override fun findById(applicantId: Long): Applicant? =
        try {
            stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .getApplicant(GetApplicantRequest.newBuilder().setApplicantId(applicantId).build())
                .toApplicant()
        } catch (e: StatusRuntimeException) {
            // 0 이하 id 는 application 이 INVALID_ARGUMENT 로 거절한다. 없는 지원자와 같다.
            if (e.status.code in NO_APPLICANT) null else throw ApplicantLookupFailedException(applicantId, e)
        }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun ApplicantResponse.toApplicant() = Applicant(
        userId = userId,
        name = name.takeIf { hasName() },
        schoolName = schoolName.takeIf { hasSchoolName() },
        region = when (region) {
            GrpcRegion.REGION_DAEJEON -> Applicant.Region.DAEJEON
            GrpcRegion.REGION_NATIONAL -> Applicant.Region.NATIONAL
            else -> null
        },
        admissionType = when (admissionType) {
            GrpcAdmissionType.ADMISSION_TYPE_REGULAR -> Applicant.AdmissionType.REGULAR
            GrpcAdmissionType.ADMISSION_TYPE_MEISTER -> Applicant.AdmissionType.MEISTER
            GrpcAdmissionType.ADMISSION_TYPE_SOCIAL -> Applicant.AdmissionType.SOCIAL
            else -> null
        },
        photoFileId = photoFileId.takeIf { hasPhotoFileId() },
    )

    private companion object {
        val NO_APPLICANT = setOf(Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT)
    }
}
