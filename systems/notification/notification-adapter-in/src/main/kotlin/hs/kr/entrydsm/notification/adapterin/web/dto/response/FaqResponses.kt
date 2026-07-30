package hs.kr.entrydsm.notification.adapterin.web.dto.response

import java.time.LocalDateTime

data class FaqSummaryResponse(
    val faqId: Long,
    val category: String,
    val question: String,
    val answer: String,
)

data class FaqDetailResponse(
    val faqId: Long,
    val category: String,
    val question: String,
    val answer: String,
    val viewCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

