package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileMetadataResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadFileResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val CATEGORY = FileCategory.APPLICATION

@RestController
@RequestMapping("/api/document/v11/application")
class ApplicationFileController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
    private val readFileUseCase: ReadFileUseCase,
) {

    @PostMapping
    fun save(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("receiptCode") receiptCode: String,
    ): ApiResponse<UploadFileResponse> {
        val fileName = FileNaming.applicationFileName(receiptCode, file.requireExtension(CATEGORY))
        val saved = file.inputStream.use {
            uploadFileUseCase.upload(file.toUploadCommand(CATEGORY, fileName), it)
        }
        return ApiResponse.success(UploadFileResponse.from(saved))
    }

    @GetMapping
    fun find(@RequestParam("receiptCode") receiptCode: String): ApiResponse<FileMetadataResponse> {
        val stored = FileExtension.DOCUMENT_FORMATS.firstNotNullOfOrNull { extension ->
            readFileUseCase.findByFileName(CATEGORY, FileNaming.applicationFileName(receiptCode, extension))
        }
        if (stored != null) {
            return ApiResponse.success(
                FileMetadataResponse(key = stored.objectKey, fileName = stored.fileName, exists = true)
            )
        }
        val defaultFileName = FileNaming.applicationFileName(receiptCode, FileExtension.PDF)
        return ApiResponse.success(
            FileMetadataResponse(
                key = CATEGORY.objectKeyOf(defaultFileName),
                fileName = defaultFileName,
                exists = false,
            )
        )
    }

    @GetMapping("/download")
    fun download(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestParam("format", defaultValue = "pdf") format: String,
    ): ApiResponse<DownloadUrlResponse> {
        val extension = FileExtension.fromExtension(format)?.takeIf(CATEGORY::supports)
            ?: throw IllegalArgumentException("Unsupported format: $format")
        val fileName = FileNaming.applicationFileName(receiptCode, extension)
        return ApiResponse.success(
            DownloadUrlResponse.from(
                issueDownloadUrlUseCase.issueByCommand(IssueDownloadUrlCommand(CATEGORY, fileName))
            )
        )
    }
}
