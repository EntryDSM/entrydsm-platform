package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toCreateResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.request.AnswerQuestionRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.request.CreateExportRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.request.CreateNoticeRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.response.CreateExportResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ExportJobResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.NoticeResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.QuestionAnswerResponse
import hs.kr.entrydsm.admin.domain.command.AnswerQuestionCommand
import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.`in`.AnswerQuestionUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadExportUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class SupportController(
    private val createExportUseCase: CreateExportUseCase,
    private val readExportUseCase: ReadExportUseCase,
    private val createNoticeUseCase: CreateNoticeUseCase,
    private val answerQuestionUseCase: AnswerQuestionUseCase,
) {

    @PostMapping(AdminEndpointPaths.EXPORTS)
    fun createExport(
        @Valid @RequestBody request: CreateExportRequest,
    ): ResponseEntity<ApiResponse<CreateExportResponse>> {
        val job = createExportUseCase.create(
            CreateExportCommand(
                type = request.type!!,
                filter = ApplicantFilter(
                    admissionTypes = request.filter?.admissionTypes.orEmpty(),
                    statuses = request.filter?.statuses.orEmpty(),
                ),
            ),
        )
        return ResponseEntity.accepted().body(ApiResponse(data = job.toCreateResponse()))
    }

    @GetMapping(AdminEndpointPaths.EXPORT)
    fun findExport(
        @PathVariable exportJobId: String,
    ): ResponseEntity<ApiResponse<ExportJobResponse>> =
        ResponseEntity.ok(ApiResponse(data = readExportUseCase.findById(exportJobId).toResponse()))

    @PostMapping(AdminEndpointPaths.NOTICES)
    fun createNotice(
        @Valid @RequestBody request: CreateNoticeRequest,
    ): ResponseEntity<ApiResponse<NoticeResponse>> {
        val notice = createNoticeUseCase.create(
            CreateNoticeCommand(
                title = request.title,
                content = request.content,
                isPinned = request.isPinned,
                attachmentIds = request.attachmentIds,
            ),
        )
        return ResponseEntity.status(201).body(ApiResponse(data = notice.toResponse()))
    }

    @PostMapping(AdminEndpointPaths.QUESTION_ANSWERS)
    fun answerQuestion(
        @PathVariable questionId: Long,
        @RequestHeader(USER_ID_HEADER) userId: String,
        @Valid @RequestBody request: AnswerQuestionRequest,
    ): ResponseEntity<ApiResponse<QuestionAnswerResponse>> {
        val answer = answerQuestionUseCase.answer(
            AnswerQuestionCommand(
                questionId = questionId,
                content = request.content,
                answeredBy = userId,
            ),
        )
        return ResponseEntity.status(201).body(ApiResponse(data = answer.toResponse()))
    }
}
