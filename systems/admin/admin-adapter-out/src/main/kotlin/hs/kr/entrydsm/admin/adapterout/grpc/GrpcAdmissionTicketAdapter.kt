package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.configuration.grpc.AdmissionTicketTarget
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsRequest
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 수험표 xlsx 를 document(configuration) 에서 받습니다. 양식과 증명사진은 document 가 넣습니다.
 */
@Component
class GrpcAdmissionTicketAdapter(
    private val grpc: ConfigurationGrpcChannel,
) : AdmissionTicketPort {
    private val stub = ConfigurationServiceGrpc.newBlockingStub(grpc.channel)

    override fun render(tickets: List<Pair<Long, String?>>): ByteArray =
        try {
            // 기한은 한 장 기준이다. document 가 지원자마다 application 조회와 사진 받기를 차례로 한다.
            stub.withDeadlineAfter(grpc.deadlineMs * maxOf(tickets.size, 1), TimeUnit.MILLISECONDS)
                .renderAdmissionTickets(
                    RenderAdmissionTicketsRequest.newBuilder()
                        .addAllTickets(
                            tickets.map { (applicantId, examineeNumber) ->
                                AdmissionTicketTarget.newBuilder()
                                    .setApplicantId(applicantId)
                                    .also { builder -> examineeNumber?.let(builder::setExamineeNumber) }
                                    .build()
                            },
                        )
                        .build(),
                )
                .xlsx
                .toByteArray()
        } catch (exception: StatusRuntimeException) {
            // 내보내기 작업은 비동기라 이 오류는 응답으로 나가지 않고 작업 실패 로그에만 남는다.
            throw exception.toAdminException(
                notFound = ErrorCode.APPLICANT_NOT_FOUND,
                unavailable = ErrorCode.ADMISSION_TICKET_GENERATION_FAILED,
            )
        }
}
