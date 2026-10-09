package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class FirstScreeningScheduler(
    private val schedules: ScheduleRepository,
    private val screening: EvaluateFirstScreeningUseCase,
    private val clock: Clock,
) {
    /** 일정 점유와 결과 저장이 함께 성공해야 처리 완료로 남는다. */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    fun evaluateDue() {
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"))
        if (schedules.claimFirstScreening("1차 합격 발표", now)) {
            screening.evaluateFirst(EvaluateScreeningCommand(dryRun = false))
        }
    }
}
