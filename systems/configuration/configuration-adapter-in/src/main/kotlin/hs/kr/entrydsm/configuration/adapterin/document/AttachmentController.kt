package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadAttachmentResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val CATEGORY = FileCategory.ATTACHMENT

@RestController
@RequestMapping("/api/document/v11/attachment")
class AttachmentController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
) {

    @PostMapping
    fun save(@RequestParam("file") file: MultipartFile): ApiResponse<UploadAttachmentResponse> {
        file.requireExtension(CATEGORY)
        val fileName = FileNaming.attachmentFileName(file.originalFilename.orEmpty())
        val saved = file.inputStream.use {
            uploadFileUseCase.upload(file.toUploadCommand(CATEGORY, fileName), it)
        }
        return ApiResponse.success(
            UploadAttachmentResponse(
                attachmentId = FileReferenceId.of(CATEGORY, requireNotNull(saved.id)),
                key = saved.objectKey,
                fileName = saved.originalName,
                size = saved.sizeBytes,
            )
        )
    }

    @GetMapping("/download")
    fun download(@RequestParam("attachmentId") attachmentId: String): ApiResponse<DownloadUrlResponse> =
        ApiResponse.success(
            DownloadUrlResponse.from(
                issueDownloadUrlUseCase.issueById(FileReferenceId.parse(CATEGORY, attachmentId))
            )
        )
}
