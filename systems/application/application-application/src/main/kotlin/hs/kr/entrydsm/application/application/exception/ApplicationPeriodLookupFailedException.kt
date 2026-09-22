package hs.kr.entrydsm.application.application.exception

class ApplicationPeriodLookupFailedException(
    cause: Throwable,
) : RuntimeException("application period lookup failed", cause)
