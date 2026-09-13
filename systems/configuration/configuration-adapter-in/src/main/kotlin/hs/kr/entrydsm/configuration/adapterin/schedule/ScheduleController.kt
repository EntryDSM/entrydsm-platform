package hs.kr.entrydsm.configuration.adapterin.schedule

import hs.kr.entrydsm.configuration.domain.schedule.Schedule
import hs.kr.entrydsm.configuration.domain.schedule.port.`in`.ScheduleUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import java.time.ZoneId

private val SEOUL = ZoneId.of("Asia/Seoul")

@RestController
@RequestMapping("/api/schedule/v11")
class ScheduleController(
    private val scheduleUseCase: ScheduleUseCase,
) {
    @GetMapping("/schedules")
    fun findAll(): ScheduleApiResponse<List<ScheduleResponse>> {
        val schedules = scheduleUseCase.findByYear(LocalDateTime.now(SEOUL).year).map(ScheduleResponse::from)
        return ScheduleApiResponse(200, "일정 목록 조회 성공", schedules)
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ScheduleRequest): ScheduleApiResponse<ScheduleResponse> =
        ScheduleApiResponse(
            "SUCCESS",
            "일정 추가가 완료되었습니다.",
            ScheduleResponse.from(
                scheduleUseCase.create(
                    request.title,
                    request.startAt.toLocalDateTime(),
                    request.endAt.toLocalDateTime(),
                ),
            ),
        )

    @PatchMapping("/schedules/bulk")
    fun updateAll(@RequestBody request: List<ScheduleRequest>): ScheduleApiResponse<List<ScheduleResponse>> {
        require(request.isNotEmpty()) { "수정할 일정이 없습니다." }
        return ScheduleApiResponse(
            "SUCCESS",
            "일정 수정이 완료되었습니다.",
            scheduleUseCase.updateAll(request.map(ScheduleRequest::toDomain)).map(ScheduleResponse::from),
        )
    }

    @GetMapping("/time")
    fun currentTime(): ScheduleApiResponse<CurrentTimeResponse> = ScheduleApiResponse(
        "SUCCESS",
        "현재 시간 조회에 성공하였습니다.",
        CurrentTimeResponse(DateTimeResponse.from(LocalDateTime.now(SEOUL))),
    )
}

data class ScheduleApiResponse<T>(
    val status: Any,
    val message: String,
    val data: T,
)

data class ScheduleRequest(
    val title: String,
    val startAt: DateTimeRequest,
    val endAt: DateTimeRequest,
) {
    fun toDomain() = Schedule(
        title = title,
        startAt = startAt.toLocalDateTime(),
        endAt = endAt.toLocalDateTime(),
    )
}

data class DateTimeRequest(
    val year: Int,
    val month: Int,
    val day: Int,
    val dayOfWeek: String,
    val hour: Int,
    val minute: Int,
    val second: Int,
) {
    fun toLocalDateTime(): LocalDateTime = LocalDateTime.of(year, month, day, hour, minute, second).also {
        require(it.dayOfWeek.name.take(3) == dayOfWeek.uppercase()) { "요일이 날짜와 일치하지 않습니다." }
    }
}

data class ScheduleResponse(
    val scheduleId: Long,
    val title: String,
    val startAt: DateTimeResponse,
    val endAt: DateTimeResponse,
) {
    companion object {
        fun from(schedule: Schedule) = ScheduleResponse(
            requireNotNull(schedule.id),
            schedule.title,
            DateTimeResponse.from(schedule.startAt),
            DateTimeResponse.from(schedule.endAt),
        )
    }
}

data class DateTimeResponse(
    val year: Int,
    val month: Int,
    val day: Int,
    val dayOfWeek: String,
    val hour: Int,
    val minute: Int,
    val second: Int,
) {
    companion object {
        fun from(value: LocalDateTime) = DateTimeResponse(
            value.year,
            value.monthValue,
            value.dayOfMonth,
            value.dayOfWeek.name.take(3),
            value.hour,
            value.minute,
            value.second,
        )
    }
}

data class CurrentTimeResponse(val currentTime: DateTimeResponse)
