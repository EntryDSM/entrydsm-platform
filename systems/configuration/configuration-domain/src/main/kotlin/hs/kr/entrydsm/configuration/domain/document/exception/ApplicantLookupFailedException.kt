package hs.kr.entrydsm.configuration.domain.document.exception

class ApplicantLookupFailedException(applicantId: Long, cause: Throwable? = null) :
    RuntimeException("Applicant lookup failed: applicantId=$applicantId (${cause?.message})", cause)
