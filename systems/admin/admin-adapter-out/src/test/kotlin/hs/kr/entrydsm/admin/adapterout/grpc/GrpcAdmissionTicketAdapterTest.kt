package hs.kr.entrydsm.admin.adapterout.grpc

import com.google.protobuf.ByteString
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsResponse
import io.grpc.Context
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 수험표 xlsx 를 document 에서 받는다. 실제 직렬화를 거쳐 확인한다. */
class GrpcAdmissionTicketAdapterTest {

    @Test
    fun `지원자 번호와 수험 번호를 순서대로 넘기고 xlsx 바이트를 받는다`() {
        val service = FakeConfigurationService { request ->
            request.ticketsList.joinToString("|") { target ->
                "${target.applicantId}-${target.examineeNumber.takeIf { target.hasExamineeNumber() }}"
            }.toByteArray()
        }

        withAdapter(service) { adapter ->
            assertEquals("13-100002|12-null", String(adapter.render(listOf(13L to "100002", 12L to null))))
        }
    }

    @Test
    fun `증명사진이 인원수만큼 든 xlsx 도 gRPC 기본 수신 한도 4MB 에 걸리지 않는다`() {
        val large = ByteArray(20 * 1024 * 1024)

        withAdapter(FakeConfigurationService { large }) { adapter ->
            assertEquals(large.size, adapter.render(listOf(12L to "100001")).size)
        }
    }

    @Test
    fun `기한은 한 장 기준이라 장수만큼 늘려 기다린다`() {
        var remainingMs = 0L
        val service = FakeConfigurationService {
            remainingMs = checkNotNull(Context.current().deadline).timeRemaining(TimeUnit.MILLISECONDS)
            ByteArray(0)
        }

        withAdapter(service, deadlineMs = 1000) { adapter -> adapter.render(listOf(1L to null, 2L to null, 3L to null)) }

        assertTrue("$remainingMs", remainingMs in 2001..3000)
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
                assertThrows(AdminDomainException::class.java) { adapter.render(listOf(12L to "100001")) }
            }

            assertEquals(code, failure.errorCode)
        }
    }

    private fun <T> withAdapter(
        service: FakeConfigurationService,
        deadlineMs: Long = 3000,
        block: (GrpcAdmissionTicketAdapter) -> T,
    ): T {
        val server = ServerBuilder.forPort(0).addService(service).build().start()
        val channel = ConfigurationGrpcChannel("localhost", server.port, deadlineMs)
        return try {
            block(GrpcAdmissionTicketAdapter(channel))
        } finally {
            channel.destroy()
            server.shutdownNow()
        }
    }

    private class FakeConfigurationService(
        private val tickets: (RenderAdmissionTicketsRequest) -> ByteArray,
    ) : ConfigurationServiceGrpc.ConfigurationServiceImplBase() {

        override fun renderAdmissionTickets(
            request: RenderAdmissionTicketsRequest,
            responseObserver: StreamObserver<RenderAdmissionTicketsResponse>,
        ) {
            val xlsx = try {
                tickets(request)
            } catch (failure: StatusRuntimeException) {
                return responseObserver.onError(failure)
            }
            responseObserver.onNext(RenderAdmissionTicketsResponse.newBuilder().setXlsx(ByteString.copyFrom(xlsx)).build())
            responseObserver.onCompleted()
        }
    }
}
