package hs.kr.entrydsm.admin.adapterin.web

/**
 * 관리자 API 경로 상수입니다.
 *
 * Notion 명세의 경로를 그대로 쓰되, 명세에 있던 오타(슬래시 중복, `damin`)는 바로잡았습니다.
 *
 * 지원자 한 명의 수험표·원서 원본은 파일을 가진 document 서비스
 * (`/api/document/v11/admission-tickets/{applicantId}`, `/applications/{applicantId}`)가 줍니다.
 */
object AdminEndpointPaths {
    const val BASE = "/api/v11/admin"

    const val APPLICANTS = "$BASE/applicants"
    const val APPLICANT = "$APPLICANTS/{applicantId}"
    const val APPLICANT_ARRIVAL = "$APPLICANT/arrival"
    const val APPLICANT_STATUS = "$APPLICANT/status"

    const val EXAMINEE_NUMBER_ISSUE = "$BASE/examinee-numbers/issue"
    const val SCORE_POLICY = "$BASE/score-policy"
    const val ADMISSION_QUOTAS = "$BASE/admission-quotas"
    const val FIRST_SCREENING_RESULTS = "$BASE/screenings/first/results"
    const val FINAL_SCREENING_RESULT = "$BASE/screenings/final/results/{applicantId}"
    const val STATISTICS = "$BASE/statistics"
    const val ADMISSION_FILE = "$BASE/admission-file"
    const val EXPORTS = "$BASE/exports"
    const val EXPORT = "$EXPORTS/{exportJobId}"
    const val NOTICES = "$BASE/notices"
    const val NOTICE = "$NOTICES/{noticeId}"
    const val QUESTION_ANSWERS = "$BASE/questions/{questionId}/answers"
}
