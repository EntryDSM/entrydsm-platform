package hs.kr.entrydsm.application.application.exception

class ApplicantAccessDeniedException(
    applicantId: Long,
) : RuntimeException("Applicant access denied: $applicantId")
