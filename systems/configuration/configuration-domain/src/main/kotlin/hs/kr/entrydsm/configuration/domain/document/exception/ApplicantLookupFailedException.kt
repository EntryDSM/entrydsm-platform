package hs.kr.entrydsm.configuration.domain.document.exception

class ApplicantLookupFailedException(id: Long, key: String = "applicantId", cause: Throwable? = null) :
    RuntimeException("Applicant lookup failed: $key=$id (${cause?.message})", cause)
