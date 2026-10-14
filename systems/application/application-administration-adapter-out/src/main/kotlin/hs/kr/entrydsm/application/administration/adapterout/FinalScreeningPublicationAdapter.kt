package hs.kr.entrydsm.application.administration.adapterout

import hs.kr.entrydsm.application.administration.adapterout.repository.ScheduleJpaRepository
import hs.kr.entrydsm.application.application.port.out.FinalScreeningPublicationPolicy
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class FinalScreeningPublicationAdapter(
    private val schedules: ScheduleJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : FinalScreeningPublicationPolicy {
    /** 최종 발표 시각에 도달하고 발표 트랜잭션이 완료된 결과만 공개한다. */
    override fun isPublished(): Boolean {
        val schedule = schedules.findByTitle("최종 합격 발표") ?: return false
        return schedule.finalScreeningProcessedAt != null &&
            !LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")).isBefore(schedule.startAt)
    }
}
