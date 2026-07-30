package hs.kr.entrydsm.notification.adapterin.web.dto.common

import hs.kr.entrydsm.notification.adapterin.web.dto.response.FaqDetailResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.FaqSummaryResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.NoticeDetailResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.NoticeSummaryResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.PageResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.RecruitmentGuidelineResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.RecruitmentScheduleResponse
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.FaqSummaryResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeSummaryResult
import hs.kr.entrydsm.notification.application.port.`in`.result.PageResult
import hs.kr.entrydsm.notification.application.port.`in`.result.RecruitmentGuidelineResult

fun NoticeSummaryResult.toResponse(): NoticeSummaryResponse =
    NoticeSummaryResponse(
        noticeId = noticeId,
        title = title,
        author = author,
        createdAt = createdAt,
    )

fun NoticeDetailResult.toResponse(): NoticeDetailResponse =
    NoticeDetailResponse(
        noticeId = noticeId,
        title = title,
        content = content,
        author = author,
        viewCount = viewCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun FaqSummaryResult.toResponse(): FaqSummaryResponse =
    FaqSummaryResponse(
        faqId = faqId,
        category = category,
        question = question,
        answer = answer,
    )

fun FaqDetailResult.toResponse(): FaqDetailResponse =
    FaqDetailResponse(
        faqId = faqId,
        category = category,
        question = question,
        answer = answer,
        viewCount = viewCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun RecruitmentGuidelineResult.toResponse(): RecruitmentGuidelineResponse =
    RecruitmentGuidelineResponse(
        recruitmentId = recruitmentId,
        title = title,
        description = description,
        schedule = RecruitmentScheduleResponse(
            applicationStart = schedule.applicationStart,
            applicationEnd = schedule.applicationEnd,
            resultAt = schedule.resultAt,
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun <T, R> PageResult<T>.toResponse(mapper: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(mapper),
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        last = last,
    )
