package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketRequest
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 수험표 한 장을 document(configuration) 에서 받습니다. 양식과 증명사진은 document 가 넣습니다.
 */
@Component
class GrpcAdmissionTicketAdapter(
    private val grpc: ConfigurationGrpcChannel,
) : AdmissionTicketPort {
    private val stub = ConfigurationServiceGrpc.newBlockingStub(grpc.channel)

    override fun render(applicantId: Long, examineeNumber: String?): ByteArray =
        try {
            stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS)
                .renderAdmissionTicket(
                    RenderAdmissionTicketRequest.newBuilder()
                        .setApplicantId(applicantId)
                        .also { builder -> examineeNumber?.let(builder::setExamineeNumber) }
                        .build(),
                )
                .pdf
                .toByteArray()
        } catch (exception: StatusRuntimeException) {
            // 내보내기 작업은 비동기라 이 오류는 응답으로 나가지 않고 작업 실패 로그에만 남는다.
            throw exception.toAdminException(
                notFound = ErrorCode.APPLICANT_NOT_FOUND,
                unavailable = ErrorCode.ADMISSION_TICKET_GENERATION_FAILED,
            )
        }
}
