package hs.kr.entrydsm.admin.domain.enum

/**
 * 지원자의 전형 진행 상태입니다.
 *
 * 정상 흐름은 `PENDING` -> 1차 결과 -> 최종 결과 순으로만 진행합니다.
 * 관리자가 정정을 위해 이 흐름을 벗어나야 하는 경우에는 강제 변경을 사용합니다.
 */
enum class ApplicantStatus {
    PENDING,
    FIRST_PASS,
    FIRST_FAIL,
    FINAL_PASS,
    FINAL_FAIL,
    ;

    /**
     * 정상 흐름에서 [next] 상태로 넘어갈 수 있는지 판단합니다.
     */
    fun canTransitionTo(next: ApplicantStatus): Boolean = next in allowedNextStatuses()

    private fun allowedNextStatuses(): Set<ApplicantStatus> = when (this) {
        PENDING -> setOf(FIRST_PASS, FIRST_FAIL)
        FIRST_PASS -> setOf(FINAL_PASS, FINAL_FAIL)
        FIRST_FAIL, FINAL_PASS, FINAL_FAIL -> emptySet()
    }
}
