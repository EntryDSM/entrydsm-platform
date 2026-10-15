package hs.kr.entrydsm.application.administration.adapterout

import hs.kr.entrydsm.application.administration.adapterout.repository.ScheduleJpaRepository
import hs.kr.entrydsm.application.application.port.out.FinalScreeningPublicationPolicy
import org.springframework.stereotype.Component

@Component
class FinalScreeningPublicationAdapter(
    private val schedules: ScheduleJpaRepository,
) : FinalScreeningPublicationPolicy {
    /**
     * 발표 트랜잭션이 끝난 결과만 공개한다. 처리 시각은 발표 시각이 지나야 찍히므로 시각은 다시 보지 않는다.
     * 발표 뒤 관리자가 일정을 옮겨도 이미 알린 결과를 다시 숨기지 않는다.
     */
    override fun isPublished(): Boolean =
        schedules.findByTitle("최종 합격 발표")?.finalScreeningProcessedAt != null
}
