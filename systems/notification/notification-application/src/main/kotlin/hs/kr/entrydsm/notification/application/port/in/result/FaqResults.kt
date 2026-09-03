package hs.kr.entrydsm.notification.application.port.`in`.result

import java.time.LocalDateTime

data class FaqSummaryResult(
    val faqId: Long,
    val category: String,
    val question: String,
    val answer: String,
)

data class FaqDetailResult(
    val faqId: Long,
    val category: String,
    val question: String,
    val answer: String,
    val viewCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

