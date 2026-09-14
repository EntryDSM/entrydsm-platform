package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileMetadataResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 파일 다운로드·조회 API. 적재는 [DocumentUploadController] 가 맡는다.
 * 누가 다운로드할 수 있는지는 FileCategory.downloaders 가 정한다.
 */
@RestController
@RequestMapping("/api/document/v11")
class DocumentDownloadController(
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
    private val readFileUseCase: ReadFileUseCase,
) {

    @GetMapping("/application")
    fun findApplication(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileMetadataResponse> {
        val stored = readFileUseCase.findApplication(receiptCode, requester)
        val fileName = stored?.fileName ?: FileNaming.applicationFileName(receiptCode, FileExtension.PDF)
        return ApiResponse.success(
            FileMetadataResponse(
                key = stored?.objectKey ?: FileCategory.APPLICATION.objectKeyOf(fileName),
                fileName = fileName,
                exists = stored != null,
            )
        )
    }

    @GetMapping("/application/download")
    fun downloadApplication(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestParam("format", defaultValue = "pdf") format: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<DownloadUrlResponse> {
        val category = FileCategory.APPLICATION
        val fileName = FileNaming.applicationFileName(receiptCode, requireDownloadFormat(format, category))
        return issue(IssueDownloadUrlCommand(category, fileName, requester, receiptCode))
    }

    @GetMapping("/admission-ticket/download")
    fun downloadAdmissionTicket(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestParam("format", defaultValue = "pdf") format: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<DownloadUrlResponse> {
        val category = FileCategory.ADMISSION_TICKET
        val fileName = FileNaming.admissionTicketFileName(receiptCode, requireDownloadFormat(format, category))
        return issue(IssueDownloadUrlCommand(category, fileName, requester, receiptCode))
    }

    @GetMapping("/applicant-list/download")
    fun downloadApplicantList(
        @RequestParam("fileName") fileName: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<DownloadUrlResponse> =
        issue(IssueDownloadUrlCommand(FileCategory.APPLICANT_LIST, fileName, requester))

    @GetMapping("/attachment/download")
    fun downloadAttachment(
        @RequestParam("attachmentId") attachmentId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<DownloadUrlResponse> =
        issueById(FileCategory.ATTACHMENT, attachmentId, requester)

    @GetMapping("/guideline/download")
    fun downloadGuideline(
        @RequestParam("guidelineId") guidelineId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<DownloadUrlResponse> =
        issueById(FileCategory.GUIDELINE, guidelineId, requester)

    private fun issue(command: IssueDownloadUrlCommand) =
        ApiResponse.success(DownloadUrlResponse.from(issueDownloadUrlUseCase.issueByCommand(command)))

    private fun issueById(category: FileCategory, referenceId: String, requester: Requester) = ApiResponse.success(
        DownloadUrlResponse.from(
            issueDownloadUrlUseCase.issueById(category, FileReferenceId.parse(category, referenceId), requester)
        )
    )
}
