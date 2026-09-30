package hs.kr.entrydsm.application.application.exception

class ApplicationValidationException(val errorCode: ApplicationErrorCode) : IllegalArgumentException(errorCode.message)
