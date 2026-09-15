package hs.kr.entrydsm.configuration.domain.document.exception

class ApplicantLookupFailedException(userId: Long, cause: Throwable? = null) :
    RuntimeException("Applicant lookup failed: userId=$userId (${cause?.message})", cause)
