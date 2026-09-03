package hs.kr.entrydsm.admin.domain.model

import java.time.Instant

/**
 * 관리자가 등록하는 공지사항입니다.
 *
 * @property isPinned 상단 고정 여부
 * @property attachmentIds 파일관리 시스템에 업로드된 첨부 문서 식별자 목록
 */
data class Notice(
    val id: Long? = null,
    val title: String,
    val content: String,
    val isPinned: Boolean = false,
    val attachmentIds: List<String> = emptyList(),
    val createdAt: Instant? = null,
)
