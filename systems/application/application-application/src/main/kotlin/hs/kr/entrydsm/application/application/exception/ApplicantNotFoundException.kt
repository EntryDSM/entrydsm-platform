package hs.kr.entrydsm.application.application.exception

class ApplicantNotFoundException(
    applicantId: Long,
) : RuntimeException("Applicant not found: $applicantId")
