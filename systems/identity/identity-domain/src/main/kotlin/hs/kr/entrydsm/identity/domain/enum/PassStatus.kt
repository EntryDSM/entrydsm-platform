package hs.kr.entrydsm.identity.domain.enum

enum class PassStatus(val description: String) {
    NOT_ANNOUNCED("발표 대기 중"),
    FIRST_PASSED("1차 전형 합격"),
    FIRST_FAILED("1차 전형 불합격"),
    FINAL_PASSED("2차 전형 최종 합격"),
    FINAL_FAILED("2차 전형 최종 불합격"),
}
