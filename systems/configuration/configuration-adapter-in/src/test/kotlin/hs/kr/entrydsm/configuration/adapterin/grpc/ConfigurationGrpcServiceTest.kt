package hs.kr.entrydsm.configuration.adapterin.grpc

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** admin 수험표 일괄 출력이 부르는 RPC. admin 은 수험번호를 넘기고 PDF 한 장을 받는다. */
class ConfigurationGrpcServiceTest {

    @Test
    fun `수험표 한 장을 PDF 바이트로 돌려주고 수험번호가 없으면 비워서 넘긴다`() {
        val calls = mutableListOf<Pair<Long, String?>>()
        val service = service { applicantId, examineeNumber ->
            calls += applicantId to examineeNumber
            "%PDF-$applicantId".toByteArray()
        }

        val issued = RecordingObserver()
        service.renderAdmissionTicket(request(12, "100001"), issued)
        service.renderAdmissionTicket(request(13, examineeNumber = null), RecordingObserver())

        assertEquals("%PDF-12", issued.value?.pdf?.toStringUtf8())
        assertTrue(issued.completed)
        assertEquals(listOf(12L to "100001", 13L to null), calls)
    }

    @Test
    fun `없는 지원자는 NOT_FOUND, application 장애는 UNAVAILABLE, 나머지는 INTERNAL 이다`() {
        mapOf(
            ApplicantNotFoundException(12) to Status.Code.NOT_FOUND,
            ApplicantLookupFailedException(12) to Status.Code.UNAVAILABLE,
            IllegalStateException("render failed") to Status.Code.INTERNAL,
        ).forEach { (failure, code) ->
            val observer = RecordingObserver()
            service { _, _ -> throw failure }.renderAdmissionTicket(request(12, examineeNumber = null), observer)

            assertEquals(code, Status.fromThrowable(observer.error).code)
        }
    }

    private fun request(applicantId: Long, examineeNumber: String?) =
        RenderAdmissionTicketRequest.newBuilder()
            .setApplicantId(applicantId)
            .also { builder -> examineeNumber?.let(builder::setExamineeNumber) }
            .build()

    private fun service(ticket: (Long, String?) -> ByteArray) = ConfigurationGrpcService(
        unused(), unused(), unused(), unused(),
        object : ApplicantFileUseCase {
            override fun renderAdmissionTicket(applicantId: Long, examineeNumber: String?) = ticket(applicantId, examineeNumber)

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

    private class RecordingObserver : StreamObserver<RenderAdmissionTicketResponse> {
        var value: RenderAdmissionTicketResponse? = null
        var error: Throwable? = null
        var completed = false

        override fun onNext(value: RenderAdmissionTicketResponse) {
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
