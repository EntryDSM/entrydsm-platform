package hs.kr.entrydsm.application.application.exception

class ApplicationCancelNotAllowedException : RuntimeException("only submitted applications can be canceled")
