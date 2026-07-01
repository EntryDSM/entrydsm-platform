package hs.kr.entrydsm.configuration.domain

data class EnvironmentVariable(
    val id: Long? = null,
    val key: String,
    val value: String,
    val description: String? = null,
)
