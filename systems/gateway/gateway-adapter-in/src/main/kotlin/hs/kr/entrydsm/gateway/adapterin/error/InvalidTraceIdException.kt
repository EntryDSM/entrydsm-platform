package hs.kr.entrydsm.gateway.adapterin.error

class InvalidTraceIdException(cause: Throwable) : RuntimeException("invalid X-Trace-Id", cause)
