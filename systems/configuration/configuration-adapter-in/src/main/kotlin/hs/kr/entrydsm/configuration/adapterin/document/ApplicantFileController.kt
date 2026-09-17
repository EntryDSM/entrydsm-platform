package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.ApplicationFileResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * 지원자 한 명에게 하나씩 있는 원서·수험표. 경로의 applicantId 는 원서 작성 API(`/api/application/v11/applicants/{id}`)와 같은 값이다.
 * 누가 적재·다운로드할 수 있는지는 FileCategory 권한표가 정한다.
 */
@RestController
@RequestMapping("/api/document/v11")
class ApplicantFileController(
    private val applicantFileUseCase: ApplicantFileUseCase,
) {

    @PutMapping("/applications/{applicantId}")
    fun uploadApplication(
        @PathVariable applicantId: Long,
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileResponse> {
        val command = file.toUploadCommand(FileCategory.APPLICATION, requester)
        val uploaded = file.inputStream.use { applicantFileUseCase.uploadApplication(applicantId, command, it) }
        return ApiResponse.success(FileResponse.ofApplicant(uploaded))
    }

    @GetMapping("/applications/{applicantId}")
    fun findApplication(
        @PathVariable applicantId: Long,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<ApplicationFileResponse> =
        ApiResponse.success(ApplicationFileResponse.of(applicantFileUseCase.findApplication(applicantId, requester)))

    /** 수험표는 올리지 않고 요청할 때마다 원서 내용으로 새로 만든다. */
    @GetMapping("/admission-tickets/{applicantId}")
    fun generateAdmissionTicket(
        @PathVariable applicantId: Long,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileResponse> =
        ApiResponse.success(FileResponse.ofApplicant(applicantFileUseCase.generateAdmissionTicket(applicantId, requester)))
}
