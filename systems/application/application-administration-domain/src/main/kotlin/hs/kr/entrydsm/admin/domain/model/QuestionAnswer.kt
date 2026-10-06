package hs.kr.entrydsm.admin.domain.model

import java.time.Instant

/**
 * 지원자가 남긴 질문에 대한 관리자 답변입니다.
 */
data class QuestionAnswer(
    val id: Long? = null,
    val questionId: Long,
    val content: String,
    val answeredBy: String,
    val answeredAt: Instant? = null,
)
