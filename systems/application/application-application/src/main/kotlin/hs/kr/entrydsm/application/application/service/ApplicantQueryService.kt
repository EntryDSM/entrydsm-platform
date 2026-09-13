package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.port.`in`.ApplicantQueryPort
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantScoreResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantSummaryResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.service.ScoreCalculator

/**
 * 원서 원본을 admin 에 넘겨주기 위한 조회입니다.
 *
 * 항목 점수는 저장하지 않고 학적으로부터 그때그때 다시 계산합니다. 산출의 소유자를
 * [ScoreCalculator] 하나로 두기 위해서입니다.
 */
class ApplicantQueryService(
    private val applicantRepository: ApplicantRepository,
    private val scoreCalculator: ScoreCalculator,
) : ApplicantQueryPort {

    override fun findAllSubmitted(): List<ApplicantSummaryResult> =
        applicantRepository.findAllSubmitted().map { it.toSummary() }

    private fun Applicant.toSummary(): ApplicantSummaryResult = ApplicantSummaryResult(
        applicantId = id,
        userId = accountId,
        name = name.orEmpty(),
        birthdate = birthdate,
        phoneNumber = phoneNumber.orEmpty(),
        region = region,
        admissionType = admissionType,
        graduationType = graduationType,
        schoolName = middleSchoolInfo?.schoolName.orEmpty(),
        applicantStatus = status,
        submittedAt = submittedAt,
        score = toScoreResult(),
        updatedAt = updatedAt,
    )

    /** 성적 산출을 한 번도 돌리지 않았으면 점수가 없는 것으로 본다. */
    private fun Applicant.toScoreResult(): ApplicantScoreResult? {
        if (totalScoreUpdatedAt == null) return null

        val breakdown = scoreCalculator.breakdown(this)
        val total = totalScore
            ?: admissionType?.let { breakdown.totalByAdmissionType[it] }
            ?: return null

        return ApplicantScoreResult(
            subjectScore = breakdown.subjectScore,
            attendanceScore = breakdown.attendanceScore,
            volunteerScore = breakdown.volunteerScore,
            totalScore = total,
        )
    }
}
