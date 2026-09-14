package hs.kr.entrydsm.application.application.exception

class ApplicantAlreadyExistsException(
    applicantId: Long,
) : RuntimeException("applicant already exists: $applicantId")
