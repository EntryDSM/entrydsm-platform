package hs.kr.entrydsm.admin.adapterin.web

/**
 * 관리자 API 경로 상수입니다.
 *
 * Notion 명세의 경로를 그대로 쓰되, 명세에 있던 오타(슬래시 중복, `damin`)와
 * 수험표만 `v1`이던 버전 불일치는 바로잡았습니다.
 */
object AdminEndpointPaths {
    const val BASE = "/api/v11/admin"

    const val APPLICANTS = "$BASE/applicants"
    const val APPLICANT = "$APPLICANTS/{applicantId}"
    const val APPLICANT_ARRIVAL = "$APPLICANT/arrival"
    const val APPLICANT_STATUS = "$APPLICANT/status"
    const val APPLICANT_ADMISSION_TICKET = "$APPLICANT/admission-ticket"
    const val APPLICANT_APPLICATION_DOCUMENT = "$APPLICANT/application-document"

    const val EXAMINEE_NUMBER_ISSUE = "$BASE/examinee-numbers/issue"
    const val SCORE_POLICY = "$BASE/score-policy"
    const val FIRST_SCREENING_RESULTS = "$BASE/screenings/first/results"
    const val FINAL_SCREENING_RESULTS = "$BASE/screenings/final/results"
    const val STATISTICS = "$BASE/statistics"
    const val EXPORTS = "$BASE/exports"
    const val EXPORT = "$EXPORTS/{exportJobId}"
    const val NOTICES = "$BASE/notices"
    const val QUESTION_ANSWERS = "$BASE/questions/{questionId}/answers"
}
