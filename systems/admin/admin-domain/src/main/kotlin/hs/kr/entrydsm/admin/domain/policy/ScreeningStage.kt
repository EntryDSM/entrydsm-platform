package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus

/**
 * 합격자 일괄 산출 단계입니다.
 *
 * @property from 산출 대상이 되는 이전 상태
 * @property pass 합격 시 부여할 상태
 * @property fail 불합격 시 부여할 상태
 */
enum class ScreeningStage(
    val from: ApplicantStatus,
    val pass: ApplicantStatus,
    val fail: ApplicantStatus,
) {
    FIRST(ApplicantStatus.PENDING, ApplicantStatus.FIRST_PASS, ApplicantStatus.FIRST_FAIL),
    FINAL(ApplicantStatus.FIRST_PASS, ApplicantStatus.FINAL_PASS, ApplicantStatus.FINAL_FAIL),
}
