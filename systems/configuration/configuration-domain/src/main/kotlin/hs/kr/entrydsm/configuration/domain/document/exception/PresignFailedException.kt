package hs.kr.entrydsm.configuration.domain.document.exception

class PresignFailedException(objectKey: String, cause: Throwable? = null) :
    RuntimeException("Failed to issue download URL: $objectKey", cause)
