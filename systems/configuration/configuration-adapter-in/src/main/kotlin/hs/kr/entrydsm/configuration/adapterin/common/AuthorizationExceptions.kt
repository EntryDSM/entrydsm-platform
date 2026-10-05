package hs.kr.entrydsm.configuration.adapterin.common

class UnauthorizedException : RuntimeException("인증이 필요합니다.")
class AccessDeniedException : RuntimeException("접근 권한이 없습니다.")
