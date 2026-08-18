package hs.kr.entrydsm.identity.application.port.`in`

interface PassPort {
    fun generatePopup(redirectUrl: String): String

    fun verify(token: String): PassVerificationResult
}

data class PassVerificationResult(
    val phoneNumber: String,
    val name: String,
)
