package hs.kr.entrydsm.configuration.adapterin.grpc

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.port.`in`.ScheduleUseCase
import hs.kr.entrydsm.configuration.grpc.AdmissionTicketTarget
import hs.kr.entrydsm.configuration.grpc.GetScheduleRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsResponse
import hs.kr.entrydsm.configuration.grpc.ScheduleResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** admin 수험표 일괄 출력과 application 원서 접수 기간 확인이 부르는 RPC. */
class ConfigurationGrpcServiceTest {

    @Test
    fun `받은 순서 그대로 넘겨 xlsx 바이트를 돌려주고 수험번호가 없으면 비워서 넘긴다`() {
        val calls = mutableListOf<List<Pair<Long, String?>>>()
        val service = service { tickets ->
            calls += tickets
            "xlsx-${tickets.size}".toByteArray()
        }

        val observer = RecordingObserver<RenderAdmissionTicketsResponse>()
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
            val observer = RecordingObserver<RenderAdmissionTicketsResponse>()
            service { throw failure }.renderAdmissionTickets(request(12L to null), observer)

            assertEquals(code, Status.fromThrowable(observer.error).code)
        }
    }

    @Test
    fun `일정은 한국 시각으로 읽어 epoch 로 주고 없는 제목은 NOT_FOUND 다`() {
        val service = service(
            schedules = schedules(
                Schedule(1, "원서 접수", LocalDateTime.of(2026, 9, 19, 9, 0), LocalDateTime.of(2026, 10, 22, 17, 0)),
            ),
        )

        val found = RecordingObserver<ScheduleResponse>()
        service.getSchedule(GetScheduleRequest.newBuilder().setTitle("원서 접수").build(), found)
        val missing = RecordingObserver<ScheduleResponse>()
        service.getSchedule(GetScheduleRequest.newBuilder().setTitle("면접").build(), missing)

        assertEquals(Instant.parse("2026-09-19T00:00:00Z").toEpochMilli(), found.value?.startAtEpochMillis)
        assertEquals(Instant.parse("2026-10-22T08:00:00Z").toEpochMilli(), found.value?.endAtEpochMillis)
        assertTrue(found.completed)
        assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(missing.error).code)
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

    private fun service(
        schedules: ScheduleUseCase = unused(),
        render: (List<Pair<Long, String?>>) -> ByteArray = { error("unused") },
    ) = ConfigurationGrpcService(
        unused(), unused(), unused(), unused(),
        object : ApplicantFileUseCase {
            override fun renderAdmissionTickets(tickets: List<Pair<Long, String?>>) = render(tickets)

            override fun generateApplicationForm(requester: Requester): DownloadableFile = error("unused")

            override fun generateApplicationForm(applicantId: Long, requester: Requester): DownloadableFile = error("unused")

            override fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile = error("unused")
        },
        schedules,
    )

    private fun schedules(vararg schedules: Schedule) = object : ScheduleUseCase {
        override fun findByTitle(title: String) = schedules.find { it.title == title }

        override fun findByYear(year: Int): List<Schedule> = error("unused")

        override fun create(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule = error("unused")

        override fun updateAll(schedules: List<Schedule>): List<Schedule> = error("unused")
    }

    /** 환경변수 RPC 처럼 이 테스트에서 부르지 않는 유스케이스. */
    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("unexpected call: ${method.name}")
        } as T

    private class RecordingObserver<T> : StreamObserver<T> {
        var value: T? = null
        var error: Throwable? = null
        var completed = false

        override fun onNext(value: T) {
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
