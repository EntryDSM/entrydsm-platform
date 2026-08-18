package hs.kr.entrydsm.identity.application.port.out

interface PassProviderPort {
    fun generatePopup(redirectUrl: String): String

    fun verify(token: String): PassIdentity
}

data class PassIdentity(
    val phoneNumber: String,
    val name: String,
)

class PassProviderException(
    val reason: Reason,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause) {
    enum class Reason {
        INVALID_RESPONSE,
        UNAVAILABLE,
    }
}
