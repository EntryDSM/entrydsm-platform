package hs.kr.entrydsm.identity.domain.model

@JvmInline
value class PasswordHash private constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Password hash must not be blank" }
    }

    companion object {
        fun fromEncoded(value: String): PasswordHash = PasswordHash(value)
    }
}
