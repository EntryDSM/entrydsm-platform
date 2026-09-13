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
    /** 답변 등록 시각. 공개 조회 응답에는 넣지 않고 답변 등록 결과에만 쓴다. */
    val answeredAt: LocalDateTime? = null,
)

