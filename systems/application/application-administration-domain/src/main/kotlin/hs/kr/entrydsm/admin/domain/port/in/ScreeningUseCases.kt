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

    /** 발표 전 개인의 합격 예정 상태만 변경한다. */
    fun selectFirst(applicantId: Long) { error("지원하지 않는 개인 선발") }
    fun cancelFirst(applicantId: Long) { error("지원하지 않는 개인 취소") }

    /** 스케줄러가 부족분 보충 후 확정 결과를 공개한다. */
    fun publishFirst(): ScreeningResult = error("지원하지 않는 발표")
}

interface EvaluateFinalScreeningUseCase {
    fun evaluateFinal(applicantId: Long): FinalScreeningResult

    /** 최종 합격 발표 전에 1차 합격자나 최종 합격자 한 명을 최종 불합격으로 바꾼다. */
    fun failFinal(applicantId: Long): FinalScreeningResult
}

interface ReadStatisticsUseCase {
    fun collect(metrics: Set<StatisticsMetric>): ApplicantStatistics
}
