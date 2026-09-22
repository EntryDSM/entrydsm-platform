package hs.kr.entrydsm.admin.adapterin.web.dto.response

import java.time.Instant

data class AdmissionFileResponse(
    val downloadUrl: String?,
    val expiresAt: Instant?,
)
