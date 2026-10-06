package hs.kr.entrydsm.configuration.adapterin.grpc

import hs.kr.entrydsm.configuration.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class ConfigurationGrpcServiceTest {
    @Test
    fun `파일 서비스는 일정과 문서 생성 RPC를 제공하지 않는다`() {
        val service = ConfigurationGrpcService(unused(), unused(), unused(), unused())
        val schedule = RecordingObserver<ScheduleResponse>()
        service.getSchedule(GetScheduleRequest.getDefaultInstance(), schedule)
        assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(schedule.error).code)
        val tickets = RecordingObserver<RenderAdmissionTicketsResponse>()
        service.renderAdmissionTickets(RenderAdmissionTicketsRequest.getDefaultInstance(), tickets)
        assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(tickets.error).code)
        val essays = RecordingObserver<RenderApplicationEssayResponse>()
        service.renderApplicationEssay(RenderApplicationEssayRequest.getDefaultInstance(), essays)
        assertEquals(Status.Code.UNIMPLEMENTED, Status.fromThrowable(essays.error).code)
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(T::class.java)) { _, _, _ -> error("삭제된 업무 RPC가 파일 서비스 업무를 호출하면 안 된다") } as T
    private class RecordingObserver<T> : StreamObserver<T> {
        var error: Throwable? = null
        override fun onNext(value: T) = Unit
        override fun onError(value: Throwable) { error = value }
        override fun onCompleted() = Unit
    }
}
