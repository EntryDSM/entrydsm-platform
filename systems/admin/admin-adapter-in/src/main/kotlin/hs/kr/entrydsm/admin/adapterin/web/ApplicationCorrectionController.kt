package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.request.CorrectApplicationRequest
import hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand
import hs.kr.entrydsm.admin.domain.port.`in`.ApplicationCorrectionResult
import hs.kr.entrydsm.admin.domain.port.`in`.CorrectApplicationUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/** 제출/도착 원서의 정정. 버전은 서버에서 관리한다. */
@RestController
class ApplicationCorrectionController(private val corrections: CorrectApplicationUseCase) {
    /** 서버가 실제 정정에 적용하는 필드와 상태 정책을 반환한다. */
    @GetMapping("${AdminEndpointPaths.APPLICANT}/application")
    fun editableFields(@PathVariable("applicantId") applicantId: Long) = ResponseEntity.ok(ApiResponse(
        data = corrections.editableFields(applicantId),
    ))

    /** 제출/도착 원서를 요청한 변경사항으로 정정한다. */
    @PatchMapping("${AdminEndpointPaths.APPLICANT}/application")
    fun correct(
        @PathVariable("applicantId") applicantId: Long,
        @Valid @RequestBody request: CorrectApplicationRequest,
    ): ResponseEntity<ApiResponse<ApplicationCorrectionResult>> = ResponseEntity.ok(ApiResponse(
        data = corrections.correct(CorrectApplicationCommand(
            applicantId, request.reason, request.changes,
        ), ""),
    ))
}
