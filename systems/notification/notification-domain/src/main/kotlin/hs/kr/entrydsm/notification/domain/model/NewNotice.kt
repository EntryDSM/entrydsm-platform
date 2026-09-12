package hs.kr.entrydsm.notification.domain.model

/**
 * 아직 저장되지 않은 공지사항입니다.
 *
 * 식별자와 조회수, 생성·수정 시각은 저장 시점에 정해지므로 [Notice] 와 분리합니다.
 */
data class NewNotice(
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val author: String,
)
