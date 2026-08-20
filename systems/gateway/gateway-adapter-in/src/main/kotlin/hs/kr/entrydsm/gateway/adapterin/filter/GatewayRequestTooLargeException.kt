package hs.kr.entrydsm.gateway.adapterin.filter

class GatewayRequestTooLargeException : RuntimeException("gateway request body exceeds configured limit")
