package hs.kr.entrydsm.application.application.port.`in`.command

data class SaveCertificatesCommand(
    val applicantId: Long,
    val isDsmAlgorithmAwarded: Boolean,
    val isProgrammingCertified: Boolean,
)
