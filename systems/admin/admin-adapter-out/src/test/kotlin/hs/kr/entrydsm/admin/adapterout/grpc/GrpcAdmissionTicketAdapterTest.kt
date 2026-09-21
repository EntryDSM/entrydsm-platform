package hs.kr.entrydsm.admin.adapterout.grpc

import com.google.protobuf.ByteString
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketResponse
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** 수험표 한 장을 document 에서 받는다. 실제 직렬화를 거쳐 확인한다. */
class GrpcAdmissionTicketAdapterTest {

    @Test
    fun `지원자 번호와 수험 번호를 넘기고 PDF 바이트를 받는다`() {
        val service = FakeConfigurationService { request ->
            val examineeNumber = request.examineeNumber.takeIf { request.hasExamineeNumber() }
            "%PDF-${request.applicantId}-$examineeNumber".toByteArray()
        }

        withAdapter(service) { adapter ->
            assertEquals("%PDF-12-100001", String(adapter.render(12, "100001")))
            assertEquals("%PDF-13-null", String(adapter.render(13, examineeNumber = null)))
        }
    }

    @Test
    fun `원본 증명사진이 든 수험표도 gRPC 기본 수신 한도 4MB 에 걸리지 않는다`() {
        val large = ByteArray(6 * 1024 * 1024)

        withAdapter(FakeConfigurationService { large }) { adapter ->
            assertEquals(large.size, adapter.render(12, "100001").size)
        }
    }

    @Test
    fun `없는 지원자와 document 장애를 admin 오류로 옮긴다`() {
        mapOf(
            Status.NOT_FOUND to ErrorCode.APPLICANT_NOT_FOUND,
            Status.UNAVAILABLE to ErrorCode.ADMISSION_TICKET_GENERATION_FAILED,
            // 새 RPC 가 없는 옛 configuration 이 떠 있을 때다.
            Status.UNIMPLEMENTED to ErrorCode.ADMISSION_TICKET_GENERATION_FAILED,
        ).forEach { (status, code) ->
            val failure = withAdapter(FakeConfigurationService { throw status.asRuntimeException() }) { adapter ->
                assertThrows(AdminDomainException::class.java) { adapter.render(12, "100001") }
            }

            assertEquals(code, failure.errorCode)
        }
    }

    private fun <T> withAdapter(service: FakeConfigurationService, block: (GrpcAdmissionTicketAdapter) -> T): T {
        val server = ServerBuilder.forPort(0).addService(service).build().start()
        val channel = ConfigurationGrpcChannel("localhost", server.port, 3000)
        return try {
            block(GrpcAdmissionTicketAdapter(channel))
        } finally {
            channel.destroy()
            server.shutdownNow()
        }
    }

    private class FakeConfigurationService(
        private val ticket: (RenderAdmissionTicketRequest) -> ByteArray,
    ) : ConfigurationServiceGrpc.ConfigurationServiceImplBase() {

        override fun renderAdmissionTicket(
            request: RenderAdmissionTicketRequest,
            responseObserver: StreamObserver<RenderAdmissionTicketResponse>,
        ) {
            val pdf = try {
                ticket(request)
            } catch (failure: StatusRuntimeException) {
                return responseObserver.onError(failure)
            }
            responseObserver.onNext(RenderAdmissionTicketResponse.newBuilder().setPdf(ByteString.copyFrom(pdf)).build())
            responseObserver.onCompleted()
        }
    }
}
