package hs.kr.entrydsm.application.administration.domain.document.exception

import hs.kr.entrydsm.configuration.domain.document.*

class ApplicantLookupFailedException(id: Long, key: String = "applicantId", cause: Throwable? = null) :
    RuntimeException("원서 조회 또는 변환에 실패했습니다.", cause)
