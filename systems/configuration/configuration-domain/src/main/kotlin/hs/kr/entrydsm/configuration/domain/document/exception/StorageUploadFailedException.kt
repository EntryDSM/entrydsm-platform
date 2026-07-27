package hs.kr.entrydsm.configuration.domain.document.exception

class StorageUploadFailedException(objectKey: String, cause: Throwable? = null) :
    RuntimeException("Failed to upload file to storage: $objectKey", cause)
