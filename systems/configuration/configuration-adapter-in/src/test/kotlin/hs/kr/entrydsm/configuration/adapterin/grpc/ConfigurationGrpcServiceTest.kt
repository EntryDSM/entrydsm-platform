package hs.kr.entrydsm.configuration.adapterin.grpc

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.configuration.grpc.AdmissionTicketTarget
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** admin 수험표 일괄 출력이 부르는 RPC. admin 은 지원자와 수험번호 목록을 넘기고 xlsx 하나를 받는다. */
class ConfigurationGrpcServiceTest {

    @Test
    fun `받은 순서 그대로 넘겨 xlsx 바이트를 돌려주고 수험번호가 없으면 비워서 넘긴다`() {
        val calls = mutableListOf<List<Pair<Long, String?>>>()
        val service = service { tickets ->
            calls += tickets
            "xlsx-${tickets.size}".toByteArray()
        }

        val observer = RecordingObserver()
        service.renderAdmissionTickets(request(13L to "100002", 12L to null), observer)

        assertEquals("xlsx-2", observer.value?.xlsx?.toStringUtf8())
        assertTrue(observer.completed)
        assertEquals(listOf(listOf(13L to "100002", 12L to null)), calls)
    }

    @Test
    fun `없는 지원자는 NOT_FOUND, application 장애는 UNAVAILABLE, 나머지는 INTERNAL 이다`() {
        mapOf(
            ApplicantNotFoundException(12) to Status.Code.NOT_FOUND,
            ApplicantLookupFailedException(12) to Status.Code.UNAVAILABLE,
            IllegalStateException("render failed") to Status.Code.INTERNAL,
        ).forEach { (failure, code) ->
            val observer = RecordingObserver()
            service { throw failure }.renderAdmissionTickets(request(12L to null), observer)

            assertEquals(code, Status.fromThrowable(observer.error).code)
        }
    }

    private fun request(vararg tickets: Pair<Long, String?>) =
        RenderAdmissionTicketsRequest.newBuilder()
            .addAllTickets(
                tickets.map { (applicantId, examineeNumber) ->
                    AdmissionTicketTarget.newBuilder()
                        .setApplicantId(applicantId)
                        .also { builder -> examineeNumber?.let(builder::setExamineeNumber) }
                        .build()
                },
            )
            .build()

    private fun service(render: (List<Pair<Long, String?>>) -> ByteArray) = ConfigurationGrpcService(
        unused(), unused(), unused(), unused(),
        object : ApplicantFileUseCase {
            override fun renderAdmissionTickets(tickets: List<Pair<Long, String?>>) = render(tickets)

            override fun generateApplicationForm(requester: Requester): DownloadableFile = error("unused")

            override fun generateApplicationForm(applicantId: Long, requester: Requester): DownloadableFile = error("unused")

            override fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile = error("unused")
        },
    )

    /** 환경변수 RPC 는 이 테스트에서 부르지 않는다. */
    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("unexpected call: ${method.name}")
        } as T

    private class RecordingObserver : StreamObserver<RenderAdmissionTicketsResponse> {
        var value: RenderAdmissionTicketsResponse? = null
        var error: Throwable? = null
        var completed = false

        override fun onNext(value: RenderAdmissionTicketsResponse) {
            this.value = value
        }

        override fun onError(t: Throwable) {
            error = t
        }

        override fun onCompleted() {
            completed = true
        }
    }
}
