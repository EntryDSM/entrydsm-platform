package hs.kr.entrydsm.configuration.domain.schedule

import java.time.LocalDateTime

data class Schedule(
    val id: Long? = null,
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
) {
    init {
        require(title.isNotBlank()) { "일정 제목은 비어 있을 수 없습니다." }
        require(!startAt.isAfter(endAt)) { "일정 시작 시각은 종료 시각보다 늦을 수 없습니다." }
    }
}
