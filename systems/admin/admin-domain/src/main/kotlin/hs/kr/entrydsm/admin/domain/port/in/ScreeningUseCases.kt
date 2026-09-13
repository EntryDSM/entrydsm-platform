package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.command.UpdateAdmissionQuotaCommand
import hs.kr.entrydsm.admin.domain.command.UpdateScorePolicyCommand
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.FinalScreeningResult
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.model.ScreeningResult

interface ReadScorePolicyUseCase {
    fun findCurrent(): ScorePolicy
}

interface UpdateScorePolicyUseCase {
    fun update(command: UpdateScorePolicyCommand)
}

interface ReadAdmissionQuotaUseCase {
    fun findCurrent(): AdmissionQuota
}

interface UpdateAdmissionQuotaUseCase {
    fun update(command: UpdateAdmissionQuotaCommand): AdmissionQuota
}

interface EvaluateFirstScreeningUseCase {
    fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult
}

interface EvaluateFinalScreeningUseCase {
    fun evaluateFinal(applicantId: Long): FinalScreeningResult
}

interface ReadStatisticsUseCase {
    fun collect(metrics: Set<StatisticsMetric>): ApplicantStatistics
}
