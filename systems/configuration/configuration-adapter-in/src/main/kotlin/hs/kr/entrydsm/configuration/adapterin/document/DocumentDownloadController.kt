package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileMetadataResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** 파일 다운로드·조회 API. 적재는 [DocumentUploadController] 가 맡는다. */
@RestController
@RequestMapping("/api/document/v11")
class DocumentDownloadController(
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
    private val readFileUseCase: ReadFileUseCase,
) {

    @GetMapping("/application")
    fun findApplication(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestHeader("X-User-Id", required = false) userId: Long?,
        @RequestHeader("X-User-Role") role: String,
    ): ApiResponse<FileMetadataResponse> {
        val category = FileCategory.APPLICATION
        val stored = FileExtension.documentFormats
            .mapNotNull { readFileUseCase.findByFileName(category, FileNaming.applicationFileName(receiptCode, it)) }
            .maxByOrNull { it.createdAt ?: Instant.EPOCH }
        requireOwner(role, userId, stored)
        if (stored != null) {
            return ApiResponse.success(FileMetadataResponse(key = stored.objectKey, fileName = stored.fileName, exists = true))
        }
        val defaultFileName = FileNaming.applicationFileName(receiptCode, FileExtension.PDF)
        return ApiResponse.success(
            FileMetadataResponse(key = category.objectKeyOf(defaultFileName), fileName = defaultFileName, exists = false)
        )
    }

    @GetMapping("/application/download")
    fun downloadApplication(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestParam("format", defaultValue = "pdf") format: String,
        @RequestHeader("X-User-Id", required = false) userId: Long?,
        @RequestHeader("X-User-Role") role: String,
    ): ApiResponse<DownloadUrlResponse> {
        val category = FileCategory.APPLICATION
        val fileName = FileNaming.applicationFileName(receiptCode, requireDownloadFormat(format, category))
        requireOwner(role, userId, readFileUseCase.findByFileName(category, fileName))
        return issue(category, fileName)
    }

    @GetMapping("/admission-ticket/download")
    fun downloadAdmissionTicket(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestParam("format", defaultValue = "pdf") format: String,
        @RequestHeader("X-User-Id", required = false) userId: Long?,
        @RequestHeader("X-User-Role") role: String,
    ): ApiResponse<DownloadUrlResponse> {
        val category = FileCategory.ADMISSION_TICKET
        val fileName = FileNaming.admissionTicketFileName(receiptCode, requireDownloadFormat(format, category))
        requireOwner(role, userId, readFileUseCase.findByFileName(category, fileName))
        return issue(category, fileName)
    }

    @GetMapping("/applicant-list/download")
    fun downloadApplicantList(@RequestParam("fileName") fileName: String): ApiResponse<DownloadUrlResponse> =
        issue(FileCategory.APPLICANT_LIST, fileName)

    @GetMapping("/attachment/download")
    fun downloadAttachment(@RequestParam("attachmentId") attachmentId: String): ApiResponse<DownloadUrlResponse> =
        issueById(FileCategory.ATTACHMENT, attachmentId)

    @GetMapping("/guideline/download")
    fun downloadGuideline(@RequestParam("guidelineId") guidelineId: String): ApiResponse<DownloadUrlResponse> =
        issueById(FileCategory.GUIDELINE, guidelineId)

    private fun issue(category: FileCategory, fileName: String) = ApiResponse.success(
        DownloadUrlResponse.from(issueDownloadUrlUseCase.issueByCommand(IssueDownloadUrlCommand(category, fileName)))
    )

    private fun issueById(category: FileCategory, referenceId: String) = ApiResponse.success(
        DownloadUrlResponse.from(issueDownloadUrlUseCase.issueById(category, FileReferenceId.parse(category, referenceId)))
    )
}
