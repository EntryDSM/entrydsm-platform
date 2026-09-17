package hs.kr.entrydsm.configuration.domain.document.exception

class ApplicantNotFoundException(applicantId: Long) :
    RuntimeException("Applicant not found: applicantId=$applicantId")
