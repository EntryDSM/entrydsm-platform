package hs.kr.entrydsm.configuration.domain.document.exception

class FileDocumentNotFoundException(objectKey: String) :
    RuntimeException("File not found: $objectKey")
