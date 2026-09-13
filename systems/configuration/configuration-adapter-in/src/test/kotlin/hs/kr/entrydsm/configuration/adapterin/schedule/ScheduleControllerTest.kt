package hs.kr.entrydsm.configuration.adapterin.schedule

import hs.kr.entrydsm.configuration.adapterin.common.DocumentExceptionHandler
import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.port.`in`.ScheduleUseCase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime
import java.time.ZoneId
import tools.jackson.module.kotlin.jacksonMapperBuilder

class ScheduleControllerTest {
    private val useCase = StubScheduleUseCase()
    private val jsonConverter = JacksonJsonHttpMessageConverter(jacksonMapperBuilder().build())
    private val mvc = MockMvcBuilders.standaloneSetup(ScheduleController(useCase))
        .setMessageConverters(jsonConverter)
        .setControllerAdvice(DocumentExceptionHandler())
        .build()
    private val securedMvc = MockMvcBuilders.standaloneSetup(ScheduleController(useCase))
        .setMessageConverters(jsonConverter)
        .addInterceptors(ScheduleAdminInterceptor())
        .setControllerAdvice(DocumentExceptionHandler())
        .build()

    @Test
    fun `현재 연도 일정은 명세의 날짜 객체로 응답한다`() {
        mvc.perform(get("/api/schedule/v11/schedules"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.message").value("일정 목록 조회 성공"))
            .andExpect(jsonPath("$.data[0].scheduleId").value(1))
            .andExpect(jsonPath("$.data[0].startAt.dayOfWeek").value("SUN"))

        assertEquals(LocalDateTime.now(ZoneId.of("Asia/Seoul")).year, useCase.requestedYear)
    }

    @Test
    fun `여러 일정을 한 요청으로 수정한다`() {
        mvc.perform(
            patch("/api/schedule/v11/schedules/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """[{"title":"원서 접수","startAt":{"year":2026,"month":4,"day":5,"dayOfWeek":"SUN","hour":21,"minute":5,"second":34},"endAt":{"year":2026,"month":5,"day":5,"dayOfWeek":"TUE","hour":21,"minute":5,"second":34}},{"title":"1차 발표","startAt":{"year":2026,"month":4,"day":5,"dayOfWeek":"SUN","hour":21,"minute":5,"second":34},"endAt":{"year":2026,"month":5,"day":5,"dayOfWeek":"TUE","hour":21,"minute":5,"second":34}}]""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].title").value("원서 접수"))
            .andExpect(jsonPath("$.data[1].title").value("1차 발표"))

        assertEquals(listOf("원서 접수", "1차 발표"), useCase.updatedTitles)
        assertEquals(LocalDateTime.of(2026, 4, 5, 21, 5, 34), useCase.updatedStartAt)
    }

    @Test
    fun `빈 배열 수정 요청은 400으로 거부한다`() {
        mvc.perform(
            patch("/api/schedule/v11/schedules/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `시작이 종료보다 늦으면 400으로 거부한다`() {
        mvc.perform(
            patch("/api/schedule/v11/schedules/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """[{"title":"원서 접수","startAt":{"year":2026,"month":5,"day":5,"dayOfWeek":"TUE","hour":21,"minute":5,"second":34},"endAt":{"year":2026,"month":4,"day":5,"dayOfWeek":"SUN","hour":21,"minute":5,"second":34}}]""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `관리자는 일정을 추가할 수 있다`() {
        securedMvc.perform(
            post("/api/schedule/v11/schedules")
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"title":"원서 접수","startAt":{"year":2026,"month":4,"day":5,"dayOfWeek":"SUN","hour":21,"minute":5,"second":34},"endAt":{"year":2026,"month":5,"day":5,"dayOfWeek":"TUE","hour":21,"minute":5,"second":34}}""",
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.title").value("원서 접수"))
    }

    @Test
    fun `인증 정보가 없는 일정 쓰기 요청은 거부한다`() {
        securedMvc.perform(
            post("/api/schedule/v11/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))
    }

    @Test
    fun `관리자가 아닌 일정 쓰기 요청은 거부한다`() {
        securedMvc.perform(
            patch("/api/schedule/v11/schedules/bulk")
                .header("X-User-Id", "1")
                .header("X-User-Role", "APPLICANT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
    }

    @Test
    fun `일정 조회 요청은 인증 없이 허용한다`() {
        securedMvc.perform(get("/api/schedule/v11/schedules"))
            .andExpect(status().isOk)
    }

    @Test
    fun `날짜와 요일이 다르면 400으로 거부한다`() {
        mvc.perform(
            patch("/api/schedule/v11/schedules/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """[{"title":"원서 접수","startAt":{"year":2026,"month":4,"day":5,"dayOfWeek":"MON","hour":21,"minute":5,"second":34},"endAt":{"year":2026,"month":5,"day":5,"dayOfWeek":"TUE","hour":21,"minute":5,"second":34}}]""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST_PARAM"))
    }

    @Test
    fun `현재 시간은 서울 기준 날짜 객체로 응답한다`() {
        mvc.perform(get("/api/schedule/v11/time"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.currentTime.year").isNumber)
            .andExpect(jsonPath("$.data.currentTime.dayOfWeek").isString)
    }

    private class StubScheduleUseCase : ScheduleUseCase {
        var requestedYear: Int? = null
        var updatedStartAt: LocalDateTime? = null
        var updatedTitles: List<String> = emptyList()

        override fun create(title: String, startAt: LocalDateTime, endAt: LocalDateTime): Schedule =
            Schedule(2, title, startAt, endAt)

        override fun findByYear(year: Int): List<Schedule> {
            requestedYear = year
            return listOf(
                Schedule(
                    1,
                    "원서 접수",
                    LocalDateTime.of(2026, 4, 5, 21, 5, 34),
                    LocalDateTime.of(2026, 5, 5, 21, 5, 34),
                ),
            )
        }

        override fun updateAll(schedules: List<Schedule>): List<Schedule> {
            updatedTitles = schedules.map(Schedule::title)
            updatedStartAt = schedules.first().startAt
            return schedules.mapIndexed { index, schedule -> schedule.copy(id = index + 1L) }
        }
    }
}
