package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.command.UpdateScorePolicyCommand
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.model.ScreeningResult

interface ReadScorePolicyUseCase {
    fun findCurrent(): ScorePolicy
}

interface UpdateScorePolicyUseCase {
    fun update(command: UpdateScorePolicyCommand)
}

interface EvaluateFirstScreeningUseCase {
    fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult
}

interface EvaluateFinalScreeningUseCase {
    fun evaluateFinal(command: EvaluateScreeningCommand): ScreeningResult
}

interface ReadStatisticsUseCase {
    fun collect(metrics: Set<StatisticsMetric>): ApplicantStatistics
}
