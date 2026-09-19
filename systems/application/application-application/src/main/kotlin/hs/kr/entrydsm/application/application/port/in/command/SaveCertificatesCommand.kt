package hs.kr.entrydsm.application.application.port.`in`.command

data class SaveCertificatesCommand(
    val accountId: Long? = null,
    val isDsmAlgorithmAwarded: Boolean,
    val isProgrammingCertified: Boolean,
)
