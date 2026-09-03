package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toDetailResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toSummaryResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.request.UpdateApplicantStatusRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.request.UpdateArrivalRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ApplicantDetailResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ApplicantSummaryResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.DownloadResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ExamineeNumberIssueResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.PageResponse
import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.command.UpdateArrivalCommand
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.port.`in`.IssueAdmissionTicketUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.IssueApplicationDocumentUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.IssueExamineeNumberUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateApplicantUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ApplicantController(
    private val readApplicantUseCase: ReadApplicantUseCase,
    private val updateApplicantUseCase: UpdateApplicantUseCase,
    private val issueExamineeNumberUseCase: IssueExamineeNumberUseCase,
    private val issueAdmissionTicketUseCase: IssueAdmissionTicketUseCase,
    private val issueApplicationDocumentUseCase: IssueApplicationDocumentUseCase,
) {

    @GetMapping(AdminEndpointPaths.APPLICANTS)
    fun search(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) regions: Set<Region>?,
        @RequestParam(required = false) admissionTypes: Set<AdmissionType>?,
        @RequestParam(required = false) graduationStatuses: Set<GraduationStatus>?,
        @RequestParam(required = false) isSubmitted: Boolean?,
        @RequestParam(required = false) statuses: Set<ApplicantStatus>?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<ApplicantSummaryResponse>>> {
        val result = readApplicantUseCase.search(
            ApplicantFilter(
                keyword = keyword,
                regions = regions.orEmpty(),
                admissionTypes = admissionTypes.orEmpty(),
                graduationStatuses = graduationStatuses.orEmpty(),
                isSubmitted = isSubmitted,
                statuses = statuses.orEmpty(),
            ),
            PageRequest(page = page, size = size),
        )

        return ResponseEntity.ok(
            ApiResponse(data = result.toResponse { it.toSummaryResponse() }),
        )
    }

    @GetMapping(AdminEndpointPaths.APPLICANT)
    fun findById(
        @PathVariable applicantId: Long,
    ): ResponseEntity<ApiResponse<ApplicantDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse(data = readApplicantUseCase.findById(applicantId).toDetailResponse()),
        )

    @PatchMapping(AdminEndpointPaths.APPLICANT_ARRIVAL)
    fun updateArrival(
        @PathVariable applicantId: Long,
        @Valid @RequestBody request: UpdateArrivalRequest,
    ): ResponseEntity<Unit> {
        updateApplicantUseCase.updateArrival(
            UpdateArrivalCommand(applicantId = applicantId, isSubmitted = request.isSubmitted!!),
        )
        return ResponseEntity.noContent().build()
    }

    @PatchMapping(AdminEndpointPaths.APPLICANT_STATUS)
    fun updateStatus(
        @PathVariable applicantId: Long,
        @Valid @RequestBody request: UpdateApplicantStatusRequest,
    ): ResponseEntity<Unit> {
        updateApplicantUseCase.updateStatus(
            UpdateApplicantStatusCommand(
                applicantId = applicantId,
                status = request.status!!,
                force = request.force,
                reason = request.reason,
            ),
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping(AdminEndpointPaths.EXAMINEE_NUMBER_ISSUE)
    fun issueExamineeNumbers(): ResponseEntity<ApiResponse<ExamineeNumberIssueResponse>> =
        ResponseEntity.ok(ApiResponse(data = issueExamineeNumberUseCase.issueAll().toResponse()))

    @GetMapping(AdminEndpointPaths.APPLICANT_ADMISSION_TICKET)
    fun issueAdmissionTicket(
        @PathVariable applicantId: Long,
    ): ResponseEntity<ApiResponse<DownloadResponse>> =
        ResponseEntity.ok(
            ApiResponse(
                data = issueAdmissionTicketUseCase.issueAdmissionTicket(applicantId).toResponse(),
            ),
        )

    @GetMapping(AdminEndpointPaths.APPLICANT_APPLICATION_DOCUMENT)
    fun issueApplicationDocument(
        @PathVariable applicantId: Long,
    ): ResponseEntity<ApiResponse<DownloadResponse>> =
        ResponseEntity.ok(
            ApiResponse(
                data = issueApplicationDocumentUseCase
                    .issueApplicationDocument(applicantId)
                    .toResponse(),
            ),
        )
}
