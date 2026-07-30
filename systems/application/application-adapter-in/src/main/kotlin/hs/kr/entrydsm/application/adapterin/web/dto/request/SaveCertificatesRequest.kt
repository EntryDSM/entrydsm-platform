package hs.kr.entrydsm.application.adapterin.web.dto.request

data class SaveCertificatesRequest(
    val applicantId: Long,
    val isDsmAlgorithmAwarded: Boolean,
    val isProgrammingCertified: Boolean,
)
