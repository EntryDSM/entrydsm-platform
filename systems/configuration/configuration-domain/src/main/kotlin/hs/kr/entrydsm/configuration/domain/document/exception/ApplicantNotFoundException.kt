package hs.kr.entrydsm.configuration.domain.document.exception

class ApplicantNotFoundException(receiptCode: String) :
    RuntimeException("Applicant not found: receiptCode=$receiptCode")
