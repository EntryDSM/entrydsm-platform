package hs.kr.entrydsm.notification.adapterout

import hs.kr.entrydsm.notification.adapterout.entity.NoticeJpaEntity
import hs.kr.entrydsm.notification.adapterout.repository.NoticeJpaRepository
import hs.kr.entrydsm.notification.adapterout.repository.NoticePersistenceAdapter
import hs.kr.entrydsm.notification.application.port.`in`.command.UpdateNoticeCommand
import hs.kr.entrydsm.notification.domain.model.NoticeCategory
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAdapterOutModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    /** null 은 기존 값을 유지하고, false 와 빈 첨부 목록은 보낸 값으로 저장한다. */
    @Test
    fun noticeUpdateKeepsNullFieldsAndStoresFalseAndEmptyAttachments() {
        val notices = InMemoryNotices(notice(isPinned = true, attachmentIds = "doc_1,doc_2"))

        val updated = NoticePersistenceAdapter(notices.repository)
            .update(UpdateNoticeCommand(noticeId = 1L, isPinned = false, attachmentIds = emptyList()))

        val row = notices.rows.getValue(1L)
        assertEquals("title", row.title)
        assertEquals("content", row.content)
        assertEquals(NoticeCategory.PROSPECTIVE_STUDENT, row.category)
        assertFalse(row.isPinned)
        assertNull(row.attachmentIds)
        assertTrue(row.updatedAt.isAfter(CREATED_AT))
        assertEquals(row.updatedAt, updated?.updatedAt)
    }

    @Test
    fun noticeUpdateReplacesProvidedFields() {
        val notices = InMemoryNotices(notice(isPinned = false, attachmentIds = null))

        NoticePersistenceAdapter(notices.repository).update(
            UpdateNoticeCommand(
                noticeId = 1L,
                title = "new title",
                content = "new content",
                category = NoticeCategory.ADMISSION_NOTICE,
                isPinned = true,
                attachmentIds = listOf("doc_3", "doc_4"),
            ),
        )

        val row = notices.rows.getValue(1L)
        assertEquals("new title", row.title)
        assertEquals("new content", row.content)
        assertEquals(NoticeCategory.ADMISSION_NOTICE, row.category)
        assertTrue(row.isPinned)
        assertEquals("doc_3,doc_4", row.attachmentIds)
    }

    @Test
    fun noticeDeleteRemovesRowAndMissingNoticeIsReported() {
        val notices = InMemoryNotices(notice(isPinned = false, attachmentIds = null))
        val adapter = NoticePersistenceAdapter(notices.repository)

        assertTrue(adapter.deleteById(1L))
        assertTrue(notices.rows.isEmpty())
        assertFalse(adapter.deleteById(1L))
        assertNull(adapter.update(UpdateNoticeCommand(noticeId = 1L, title = "new title")))
    }

    /** JpaRepository 는 메서드가 많아, 어댑터가 쓰는 조회·저장·삭제만 프록시로 흉내 낸다. */
    private class InMemoryNotices(vararg notices: NoticeJpaEntity) {
        val rows = notices.associateBy { it.id!! }.toMutableMap()

        val repository = Proxy.newProxyInstance(
            NoticeJpaRepository::class.java.classLoader,
            arrayOf(NoticeJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findById" -> Optional.ofNullable(rows[args!![0] as Long])
                "save" -> args!![0]
                "delete" -> rows.remove((args!![0] as NoticeJpaEntity).id).let { null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as NoticeJpaRepository
    }

    private fun notice(isPinned: Boolean, attachmentIds: String?): NoticeJpaEntity =
        NoticeJpaEntity(
            id = 1L,
            title = "title",
            content = "content",
            category = NoticeCategory.PROSPECTIVE_STUDENT,
            author = "관리자",
            isPinned = isPinned,
            attachmentIds = attachmentIds,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
        )

    private companion object {
        val CREATED_AT: LocalDateTime = LocalDateTime.parse("2020-01-01T00:00:00")
    }
}
