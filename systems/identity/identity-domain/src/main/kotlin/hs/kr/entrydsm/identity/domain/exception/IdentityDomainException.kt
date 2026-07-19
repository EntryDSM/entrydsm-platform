package hs.kr.entrydsm.identity.domain.exception

import hs.kr.entrydsm.identity.domain.ErrorCode

class IdentityDomainException(
    errorCode: ErrorCode,
) : IdentityException(errorCode)
