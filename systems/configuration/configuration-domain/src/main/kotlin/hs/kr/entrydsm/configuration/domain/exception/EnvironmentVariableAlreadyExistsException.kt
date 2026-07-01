package hs.kr.entrydsm.configuration.domain.exception

class EnvironmentVariableAlreadyExistsException(key: String) :
    RuntimeException("Environment variable already exists: $key")
