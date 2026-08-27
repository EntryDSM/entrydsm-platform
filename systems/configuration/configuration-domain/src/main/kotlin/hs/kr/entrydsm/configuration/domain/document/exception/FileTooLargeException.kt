package hs.kr.entrydsm.configuration.domain.document.exception

class FileTooLargeException(sizeBytes: Long, maxSizeBytes: Long) :
    RuntimeException("File size $sizeBytes bytes exceeds limit of $maxSizeBytes bytes")
