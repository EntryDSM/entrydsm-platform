package hs.kr.entrydsm.application.application.exception

class ApplicantAlreadyExistsException(
    accountId: Long,
) : RuntimeException("applicant already exists for account: $accountId")
