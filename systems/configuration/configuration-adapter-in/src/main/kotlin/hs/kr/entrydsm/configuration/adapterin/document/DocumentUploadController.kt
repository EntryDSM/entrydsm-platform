package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadAttachmentResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadFileResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadGuidelineResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadPhotoResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.GenerateAdmissionTicketUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

/**
 * 파일 적재(업로드) API. 다운로드는 [DocumentDownloadController] 가 맡는다.
 * 누가 적재할 수 있는지는 FileCategory.storers 가 정한다.
 */
@RestController
@RequestMapping("/api/document/v11")
class DocumentUploadController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
    private val generateAdmissionTicketUseCase: GenerateAdmissionTicketUseCase,
) {

    @PostMapping("/application")
    fun uploadApplication(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("receiptCode") receiptCode: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<UploadFileResponse> {
        val category = FileCategory.APPLICATION
        val fileName = FileNaming.applicationFileName(receiptCode, file.requireExtension(category))
        return ApiResponse.success(UploadFileResponse.from(file.store(category, fileName, requester, receiptCode)))
    }

    /** 수험표는 올리지 않고 서버가 만들어 적재한다. 받을 수 있는 사람(관리자, 본인)이 만든다. */
    @GetMapping("/admission-ticket")
    fun generateAdmissionTicket(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<UploadFileResponse> =
        ApiResponse.success(
            UploadFileResponse.from(generateAdmissionTicketUseCase.generateAdmissionTicket(receiptCode, requester))
        )

    @PostMapping("/applicant-list")
    fun uploadApplicantList(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("fileName", required = false) fileName: String?,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<UploadFileResponse> {
        val category = FileCategory.APPLICANT_LIST
        file.requireExtension(category)
        val targetFileName = fileName
            ?.also { if (FileExtension.fromFileName(it) != FileExtension.XLSX) throw InvalidFileFormatException(it, category) }
            ?: FileNaming.applicantListFileName(LocalDate.now())
        return ApiResponse.success(UploadFileResponse.from(file.store(category, targetFileName, requester)))
    }

    @PostMapping("/photo")
    fun uploadPhoto(
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<UploadPhotoResponse> {
        val category = FileCategory.PHOTO
        val saved = file.store(category, FileNaming.photoFileName(file.requireExtension(category)), requester)
        val downloadUrl = issueDownloadUrlUseCase.issueByCommand(
            IssueDownloadUrlCommand(category, saved.fileName, requester)
        )
        return ApiResponse.success(
            UploadPhotoResponse(
                fileId = requireNotNull(saved.id),
                key = saved.objectKey,
                fileName = saved.fileName,
                url = downloadUrl.downloadUrl,
            )
        )
    }

    @PostMapping("/attachment")
    fun uploadAttachment(
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<UploadAttachmentResponse> {
        val category = FileCategory.ATTACHMENT
        file.requireExtension(category)
        val saved = file.store(category, FileNaming.attachmentFileName(file.originalFilename.orEmpty()), requester)
        return ApiResponse.success(
            UploadAttachmentResponse(
                attachmentId = FileReferenceId.of(category, requireNotNull(saved.id)),
                key = saved.objectKey,
                fileName = saved.originalName,
                size = saved.sizeBytes,
            )
        )
    }

    @PostMapping("/guideline")
    fun uploadGuideline(
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<UploadGuidelineResponse> {
        val category = FileCategory.GUIDELINE
        file.requireExtension(category)
        val saved = file.store(category, FileNaming.attachmentFileName(file.originalFilename.orEmpty()), requester)
        return ApiResponse.success(
            UploadGuidelineResponse(
                guidelineId = FileReferenceId.of(category, requireNotNull(saved.id)),
                key = saved.objectKey,
                fileName = saved.originalName,
                size = saved.sizeBytes,
            )
        )
    }

    private fun MultipartFile.store(
        category: FileCategory,
        fileName: String,
        requester: Requester,
        receiptCode: String? = null,
    ): FileDocument =
        inputStream.use { uploadFileUseCase.upload(toUploadCommand(category, fileName, requester, receiptCode), it) }
}
