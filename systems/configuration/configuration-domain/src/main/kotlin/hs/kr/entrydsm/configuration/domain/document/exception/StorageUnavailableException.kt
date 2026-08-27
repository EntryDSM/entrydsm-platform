package hs.kr.entrydsm.configuration.domain.document.exception

class StorageUnavailableException(objectKey: String, cause: Throwable? = null) :
    RuntimeException("Storage request failed: $objectKey", cause)
