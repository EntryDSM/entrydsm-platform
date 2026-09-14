package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadAttachmentResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadFileResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadPhotoResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

/** 파일 적재(업로드) API. 다운로드는 [DocumentDownloadController] 가 맡는다. */
@RestController
@RequestMapping("/api/document/v11")
class DocumentUploadController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
) {

    @PostMapping("/application")
    fun uploadApplication(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("receiptCode") receiptCode: String,
        @RequestHeader("X-User-Id", required = false) userId: Long?,
        @RequestHeader("X-User-Role") role: String,
    ): ApiResponse<UploadFileResponse> {
        val category = FileCategory.APPLICATION
        val fileName = FileNaming.applicationFileName(receiptCode, file.requireExtension(category))
        val saved = file.store(category, fileName, userId.takeIf { role == "STUDENT" })
        return ApiResponse.success(UploadFileResponse.from(saved))
    }

    @PostMapping("/admission-ticket")
    fun uploadAdmissionTicket(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("receiptCode") receiptCode: String,
        @RequestHeader("X-User-Id", required = false) userId: Long?,
        @RequestHeader("X-User-Role") role: String,
    ): ApiResponse<UploadFileResponse> {
        val category = FileCategory.ADMISSION_TICKET
        val fileName = FileNaming.admissionTicketFileName(receiptCode, file.requireExtension(category))
        val saved = file.store(category, fileName, userId.takeIf { role == "STUDENT" })
        return ApiResponse.success(UploadFileResponse.from(saved))
    }

    @PostMapping("/applicant-list")
    fun uploadApplicantList(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("fileName", required = false) fileName: String?,
    ): ApiResponse<UploadFileResponse> {
        val category = FileCategory.APPLICANT_LIST
        file.requireExtension(category)
        val targetFileName = fileName
            ?.also { if (FileExtension.fromFileName(it) != FileExtension.XLSX) throw InvalidFileFormatException(it, category) }
            ?: FileNaming.applicantListFileName(LocalDate.now())
        return ApiResponse.success(UploadFileResponse.from(file.store(category, targetFileName)))
    }

    @PostMapping("/photo")
    fun uploadPhoto(@RequestParam("file") file: MultipartFile): ApiResponse<UploadPhotoResponse> {
        val category = FileCategory.PHOTO
        val saved = file.store(category, FileNaming.photoFileName(file.requireExtension(category)))
        val downloadUrl = issueDownloadUrlUseCase.issueByCommand(IssueDownloadUrlCommand(category, saved.fileName))
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
    fun uploadAttachment(@RequestParam("file") file: MultipartFile): ApiResponse<UploadAttachmentResponse> {
        val category = FileCategory.ATTACHMENT
        file.requireExtension(category)
        val saved = file.store(category, FileNaming.attachmentFileName(file.originalFilename.orEmpty()))
        return ApiResponse.success(
            UploadAttachmentResponse(
                attachmentId = FileReferenceId.of(category, requireNotNull(saved.id)),
                key = saved.objectKey,
                fileName = saved.originalName,
                size = saved.sizeBytes,
            )
        )
    }

    private fun MultipartFile.store(category: FileCategory, fileName: String, ownerUserId: Long? = null): FileDocument =
        inputStream.use { uploadFileUseCase.upload(toUploadCommand(category, fileName, ownerUserId), it) }
}
