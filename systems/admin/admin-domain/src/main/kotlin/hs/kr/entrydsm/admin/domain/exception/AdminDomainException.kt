package hs.kr.entrydsm.admin.domain.exception

import hs.kr.entrydsm.admin.domain.enum.ErrorCode

class AdminDomainException(
    errorCode: ErrorCode,
    cause: Throwable? = null,
) : AdminException(errorCode, cause)
