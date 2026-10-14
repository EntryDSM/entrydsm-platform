package hs.kr.entrydsm.application.administration.domain.schedule.port.out

import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import java.time.LocalDateTime

interface ScheduleRepository {
    fun findByStartAtBetween(startAt: LocalDateTime, endAt: LocalDateTime): List<Schedule>

    fun findByTitle(title: String): Schedule?

    /** 수동 변경과 발표가 같은 일정 행을 잠근다. */
    fun findByTitleForUpdate(title: String): Schedule? = findByTitle(title)

    fun save(schedule: Schedule): Schedule

    /** 도달한 미처리 발표 일정을 현재 트랜잭션에서 한 번만 점유한다. */
    fun claimFirstScreening(title: String, now: LocalDateTime): Boolean

    /** 도달한 미처리 최종 발표 일정을 현재 트랜잭션에서 한 번만 점유한다. */
    fun claimFinalScreening(title: String, now: LocalDateTime): Boolean = false
}
