package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.domain.command.ApplicationFormChanges
import hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand
import hs.kr.entrydsm.admin.domain.port.`in`.ApplicationCorrectionResult
import hs.kr.entrydsm.admin.domain.port.`in`.CorrectApplicationUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CorrectApplicationRequest(
    @field:NotBlank @field:Size(max = 500) val reason: String,
    val changes: ApplicationFormChanges,
)

/** 제출/도착 원서의 정정. 버전은 서버에서 관리한다. */
@RestController
class ApplicationCorrectionController(private val corrections: CorrectApplicationUseCase) {
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
