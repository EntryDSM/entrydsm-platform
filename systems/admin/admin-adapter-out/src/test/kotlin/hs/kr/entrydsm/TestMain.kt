package hs.kr.entrydsm.admin.adapterout

import hs.kr.entrydsm.admin.adapterout.notification.NotificationApiNoticeAdapter
import hs.kr.entrydsm.admin.adapterout.notification.NotificationApiQuestionAnswerAdapter
import hs.kr.entrydsm.admin.domain.command.UpdateNoticeCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.notification.api.AnswerQuestionRequest
import hs.kr.entrydsm.notification.api.CreateNoticeRequest
import hs.kr.entrydsm.notification.api.NoticeCreated
import hs.kr.entrydsm.notification.api.NoticeUpdated
import hs.kr.entrydsm.notification.api.NotificationApi
import hs.kr.entrydsm.notification.api.NotificationApiException
import hs.kr.entrydsm.notification.api.QuestionAnswered
import hs.kr.entrydsm.notification.api.UpdateNoticeRequest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAdapterOutModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun createNoticePostsUnderTheSchoolNameAndKeepsCreatedAt() {
        val notification = FakeNotificationApi()

        val saved = NotificationApiNoticeAdapter(notification).save(
            Notice(title = "title", content = "content", division = "ADMISSION_NOTICE", isPinned = true, attachmentIds = listOf("doc_1")),
        )

        val request = notification.created!!
        assertEquals("관리자", request.author)
        assertEquals("ADMISSION_NOTICE", request.category)
        assertEquals(listOf("doc_1"), request.attachmentIds)
        assertEquals(1L, saved.id)
        assertEquals(CREATED_AT, saved.createdAt)
    }

    /** false 와 빈 첨부 목록도 보낸 값이라 그대로 넘어가야 한다. */
    @Test
    fun updateNoticeSendsProvidedFieldsIncludingFalseAndEmptyList() {
        val notification = FakeNotificationApi()

        NotificationApiNoticeAdapter(notification).update(
            UpdateNoticeCommand(noticeId = 3L, title = "new title", isPinned = false, attachmentIds = emptyList()),
        )

        val request = notification.updated!!
        assertEquals(3L, request.noticeId)
        assertEquals("new title", request.title)
        assertEquals(false, request.isPinned)
        assertEquals(emptyList<String>(), request.attachmentIds)
    }

    /** null 인 필드를 값으로 바꿔 보내면 notification 이 기존 값을 덮어쓴다. */
    @Test
    fun updateNoticeLeavesNullFieldsNull() {
        val notification = FakeNotificationApi()

        NotificationApiNoticeAdapter(notification).update(
            UpdateNoticeCommand(noticeId = 3L, division = "PROSPECTIVE_STUDENT"),
        )

        val request = notification.updated!!
        assertEquals("PROSPECTIVE_STUDENT", request.category)
        assertNull(request.title)
        assertNull(request.content)
        assertNull(request.isPinned)
        assertNull(request.attachmentIds)
    }

    @Test
    fun deleteNoticeMapsNotFoundToNoticeNotFound() {
        val notification = FakeNotificationApi(failure = NotificationApiException.NotFound("notice not found: id=404"))

        val exception = assertThrows(AdminDomainException::class.java) {
            NotificationApiNoticeAdapter(notification).deleteById(404L)
        }

        assertEquals(404L, notification.deletedId)
        assertEquals(ErrorCode.NOTICE_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun answerQuestionMapsNotFoundToQuestionNotFound() {
        val notification = FakeNotificationApi(failure = NotificationApiException.NotFound("faq not found: id=404"))

        val exception = assertThrows(AdminDomainException::class.java) {
            NotificationApiQuestionAnswerAdapter(notification).save(questionAnswer(questionId = 404L))
        }

        assertEquals(ErrorCode.QUESTION_NOT_FOUND, exception.errorCode)
    }

    @Test
    fun answerQuestionReturnsQuestionIdAndAnsweredAt() {
        val notification = FakeNotificationApi()

        val saved = NotificationApiQuestionAnswerAdapter(notification).save(questionAnswer(questionId = 7L))

        assertEquals(7L, notification.answered?.questionId)
        assertEquals(7L, saved.id)
        assertEquals(ANSWERED_AT, saved.answeredAt)
    }

    /** 저장소 장애는 admin 오류 코드로 바꾸지 않고 그대로 올려 500 으로 떨어뜨린다. */
    @Test
    fun unexpectedNotificationFailuresPropagate() {
        val notification = FakeNotificationApi(failure = IllegalStateException("database unavailable"))

        val exception = assertThrows(IllegalStateException::class.java) {
            NotificationApiNoticeAdapter(notification).deleteById(3L)
        }

        assertFalse(exception is AdminDomainException)
    }

    private fun questionAnswer(questionId: Long) = QuestionAnswer(
        questionId = questionId,
        content = "answer",
        answeredBy = "admin",
    )

    private class FakeNotificationApi(
        private val failure: RuntimeException? = null,
    ) : NotificationApi {
        var created: CreateNoticeRequest? = null
        var updated: UpdateNoticeRequest? = null
        var deletedId: Long? = null
        var answered: AnswerQuestionRequest? = null

        override fun createNotice(request: CreateNoticeRequest): NoticeCreated {
            failure?.let { throw it }
            created = request
            return NoticeCreated(noticeId = 1L, createdAt = CREATED_AT)
        }

        override fun updateNotice(request: UpdateNoticeRequest): NoticeUpdated {
            updated = request
            failure?.let { throw it }
            return NoticeUpdated(noticeId = request.noticeId, updatedAt = UPDATED_AT)
        }

        override fun deleteNotice(noticeId: Long) {
            deletedId = noticeId
            failure?.let { throw it }
        }

        override fun answerQuestion(request: AnswerQuestionRequest): QuestionAnswered {
            failure?.let { throw it }
            answered = request
            return QuestionAnswered(questionId = request.questionId, answeredAt = ANSWERED_AT)
        }
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-09-11T09:00:00Z")
        val UPDATED_AT: Instant = Instant.parse("2026-09-14T10:00:00Z")
        val ANSWERED_AT: Instant = Instant.parse("2026-09-12T10:00:00Z")
    }
}
