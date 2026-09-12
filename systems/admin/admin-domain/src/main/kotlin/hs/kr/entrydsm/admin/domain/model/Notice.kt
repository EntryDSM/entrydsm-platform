package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.NoticeCategory
import java.time.Instant

/**
 * notification 시스템에 등록된 공지사항입니다.
 *
 * admin 은 공지를 직접 저장하지 않고 notification 에 등록을 위임하므로, 이 모델은
 * 등록 결과를 그대로 옮겨 담습니다.
 */
data class Notice(
    val id: Long,
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val author: String,
    val viewCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
