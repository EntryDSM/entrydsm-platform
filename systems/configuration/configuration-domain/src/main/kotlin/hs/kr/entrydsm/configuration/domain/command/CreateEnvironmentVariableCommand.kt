package hs.kr.entrydsm.configuration.domain.command

data class CreateEnvironmentVariableCommand(
    val key: String,
    val value: String,
    val description: String? = null,
)
