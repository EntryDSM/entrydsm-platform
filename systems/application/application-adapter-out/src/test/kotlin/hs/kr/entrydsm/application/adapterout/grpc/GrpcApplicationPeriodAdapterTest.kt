package hs.kr.entrydsm.application.adapterout.grpc

import hs.kr.entrydsm.application.application.exception.ApplicationPeriodLookupFailedException
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.GetScheduleRequest
import hs.kr.entrydsm.configuration.grpc.ScheduleResponse
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** 원서 접수 일정을 configuration 에서 받는다. 실제 직렬화를 거쳐 확인한다. */
class GrpcApplicationPeriodAdapterTest {

    @Test
    fun `원서 접수 일정을 제목으로 찾아 기간으로 돌려준다`() {
        val startAt = Instant.parse("2026-09-19T00:00:00Z")
        val endAt = Instant.parse("2026-10-22T08:00:00Z")
        val titles = mutableListOf<String>()
        val service = FakeConfigurationService { request ->
            titles += request.title
            ScheduleResponse.newBuilder()
                .setTitle(request.title)
                .setStartAtEpochMillis(startAt.toEpochMilli())
                .setEndAtEpochMillis(endAt.toEpochMilli())
                .build()
        }

        withAdapter(service) { adapter -> assertEquals(startAt..endAt, adapter.read()) }

        assertEquals(listOf("원서 접수"), titles)
    }

    @Test
    fun `일정이 없으면 null 이고 configuration 장애나 새 RPC 가 없는 configuration 은 확인 실패다`() {
        withAdapter(FakeConfigurationService { throw Status.NOT_FOUND.asRuntimeException() }) { adapter ->
            assertNull(adapter.read())
        }
        listOf(Status.UNAVAILABLE, Status.UNIMPLEMENTED, Status.INTERNAL).forEach { status ->
            withAdapter(FakeConfigurationService { throw status.asRuntimeException() }) { adapter ->
                assertThrows(ApplicationPeriodLookupFailedException::class.java) { adapter.read() }
            }
        }
    }

    private fun withAdapter(service: FakeConfigurationService, block: (GrpcApplicationPeriodAdapter) -> Unit) {
        val server = ServerBuilder.forPort(0).addService(service).build().start()
        val adapter = GrpcApplicationPeriodAdapter("localhost", server.port, 3000)
        try {
            block(adapter)
        } finally {
            adapter.destroy()
            server.shutdownNow()
        }
    }

    private class FakeConfigurationService(
        private val schedule: (GetScheduleRequest) -> ScheduleResponse,
    ) : ConfigurationServiceGrpc.ConfigurationServiceImplBase() {

        override fun getSchedule(request: GetScheduleRequest, responseObserver: StreamObserver<ScheduleResponse>) {
            val response = try {
                schedule(request)
            } catch (failure: StatusRuntimeException) {
                return responseObserver.onError(failure)
            }
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }
    }
}
