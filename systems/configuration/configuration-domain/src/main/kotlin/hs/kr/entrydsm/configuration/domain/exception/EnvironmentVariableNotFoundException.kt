package hs.kr.entrydsm.configuration.domain.exception

class EnvironmentVariableNotFoundException(key: String) :
    RuntimeException("Environment variable not found: $key")
