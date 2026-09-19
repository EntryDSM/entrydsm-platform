package hs.kr.entrydsm.configuration.domain.document.exception

/** [key] 는 찾은 열쇠 이름이다. 원서는 계정으로, 수험표는 지원자 ID 로 찾아 로그에서 갈린다. */
class ApplicantNotFoundException(id: Long, key: String = "applicantId") :
    RuntimeException("Applicant not found: $key=$id")
