package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 2차 결과는 등록 즉시가 아니라 "최종 합격 발표" 시각에 한 번에 지원자에게 알린다. */
@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class FinalScreeningScheduler(
    private val schedules: ScheduleRepository,
    private val applicants: ApplicantRepository,
    private val clock: Clock,
) {
    /** 일정 점유와 공개 이벤트 저장이 함께 성공해야 처리 완료로 남는다. */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun publishDue() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"))
        if (schedules.claimFinalScreening("최종 합격 발표", now)) {
            applicants.publishFinalResults(Instant.now(clock))
        }
    }
}
