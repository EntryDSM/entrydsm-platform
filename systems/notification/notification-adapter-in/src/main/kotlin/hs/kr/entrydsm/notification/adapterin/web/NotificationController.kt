package hs.kr.entrydsm.notification.adapterin.web

import hs.kr.entrydsm.notification.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.FaqDetailResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.FaqSummaryResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.NoticeDetailResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.NoticeSummaryResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.PageResponse
import hs.kr.entrydsm.notification.adapterin.web.dto.response.RecruitmentGuidelineResponse
import hs.kr.entrydsm.notification.application.port.`in`.NotificationPort
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadFaqPageCommand
import hs.kr.entrydsm.notification.application.port.`in`.command.ReadNotificationPageCommand
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/notification/v11/notifications")
class NotificationController(
    private val notificationPort: NotificationPort,
) {
    @GetMapping("/notification")
    fun getNotices(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) category: String?,
    ): ApiResponse<PageResponse<NoticeSummaryResponse>> {
        val result = notificationPort.getNotices(
            ReadNotificationPageCommand.of(
                page = page,
                size = size,
                category = category,
            ),
        )
        return ApiResponse(
            status = 200,
            message = "공지 목록 조회 성공",
            data = result.toResponse { it.toResponse() },
        )
    }

    @GetMapping("/notification/{id}")
    fun getNotice(
        @PathVariable id: Long,
    ): ApiResponse<NoticeDetailResponse> {
        val result = notificationPort.getNotice(id)
        return ApiResponse(
            status = 200,
            message = "공지 상세 조회 성공",
            data = result.toResponse(),
        )
    }

    @GetMapping("/qna")
    fun getFaqs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(required = false) category: String?,
    ): ApiResponse<PageResponse<FaqSummaryResponse>> {
        val result = notificationPort.getFaqs(
            ReadFaqPageCommand.of(
                page = page,
                size = size,
                category = category,
            ),
        )
        return ApiResponse(
            status = 200,
            message = "자주 묻는 질문 목록 조회 성공",
            data = result.toResponse { it.toResponse() },
        )
    }

    @GetMapping("/qna/{id}")
    fun getFaq(
        @PathVariable id: Long,
    ): ApiResponse<FaqDetailResponse> {
        val result = notificationPort.getFaq(id)
        return ApiResponse(
            status = 200,
            message = "자주 묻는 질문 상세 조회 성공",
            data = result.toResponse(),
        )
    }

    @GetMapping("/guideline")
    fun getRecruitmentGuideline(): ApiResponse<RecruitmentGuidelineResponse> {
        val result = notificationPort.getRecruitmentGuideline()
        return ApiResponse(
            status = 200,
            message = "전형 요강 상세 조회 성공",
            data = result.toResponse(),
        )
    }
}

