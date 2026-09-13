package hs.kr.entrydsm.observability.domain.exception

import hs.kr.entrydsm.observability.domain.enum.ErrorCode

class MonitorDomainException(
    errorCode: ErrorCode,
) : MonitorException(errorCode)
