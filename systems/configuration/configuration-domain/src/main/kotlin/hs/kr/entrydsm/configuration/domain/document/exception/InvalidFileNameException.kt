package hs.kr.entrydsm.configuration.domain.document.exception

class InvalidFileNameException(fileName: String) :
    RuntimeException("Invalid file name: $fileName")
