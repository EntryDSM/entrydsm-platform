package hs.kr.entrydsm.identity.domain.exception

import hs.kr.entrydsm.identity.domain.enum.ErrorCode

class IdentityDomainException(
    errorCode: ErrorCode,
    cause: Throwable? = null,
) : IdentityException(errorCode, cause)
