package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadPhotoResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val CATEGORY = FileCategory.PHOTO

@RestController
@RequestMapping("/api/document/v11/photo")
class PhotoController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
) {

    @PostMapping
    fun save(@RequestParam("file") file: MultipartFile): ApiResponse<UploadPhotoResponse> {
        val fileName = FileNaming.photoFileName(file.requireExtension(CATEGORY))
        val saved = file.inputStream.use {
            uploadFileUseCase.upload(file.toUploadCommand(CATEGORY, fileName), it)
        }
        val downloadUrl = issueDownloadUrlUseCase.issueByCommand(
            IssueDownloadUrlCommand(CATEGORY, saved.fileName)
        )
        return ApiResponse.success(
            UploadPhotoResponse(
                key = saved.objectKey,
                fileName = saved.fileName,
                url = downloadUrl.downloadUrl,
            )
        )
    }
}
