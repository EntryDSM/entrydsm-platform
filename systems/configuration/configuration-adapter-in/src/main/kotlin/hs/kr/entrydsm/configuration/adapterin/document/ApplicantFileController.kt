package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileResponse
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 지원자 한 명에게 하나씩 있는 원서·수험표. 경로에 applicantId
 * (원서 생성 API `POST /api/application/v11/applicants` 응답의 값)가 있으면 그 지원자를 찾고,
 * 없으면 요청자 본인 것을 준다. 누가 다운로드할 수 있는지는 FileCategory 권한표가 정한다.
 */
@RestController
@RequestMapping("/api/document/v11")
class ApplicantFileController(
    private val applicantFileUseCase: ApplicantFileUseCase,
) {

    /** 원서는 올리지 않고 요청할 때마다 본인 원서 내용으로 새로 만든다. */
    @GetMapping("/applications")
    fun generateApplicationForm(
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileResponse> =
        ApiResponse.success(FileResponse.ofApplicant(applicantFileUseCase.generateApplicationForm(requester)))

    /** 관리자가 지원자를 지목해 받는 원서. 학생은 자기 applicantId 로만 받을 수 있다. */
    @GetMapping("/applications/{applicantId}")
    fun generateApplicantApplicationForm(
        @PathVariable applicantId: Long,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileResponse> =
        ApiResponse.success(FileResponse.ofApplicant(applicantFileUseCase.generateApplicationForm(applicantId, requester)))

    /** 수험표는 올리지 않고 요청할 때마다 원서 내용으로 새로 만든다. */
    @GetMapping("/admission-tickets/{applicantId}")
    fun generateAdmissionTicket(
        @PathVariable applicantId: Long,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileResponse> =
        ApiResponse.success(FileResponse.ofApplicant(applicantFileUseCase.generateAdmissionTicket(applicantId, requester)))
}
