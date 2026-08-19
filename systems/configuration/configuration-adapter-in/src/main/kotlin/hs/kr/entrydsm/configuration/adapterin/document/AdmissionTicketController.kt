package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadFileResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val CATEGORY = FileCategory.ADMISSION_TICKET

@RestController
@RequestMapping("/api/document/v11/admission-ticket")
class AdmissionTicketController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
) {

    @PostMapping
    fun save(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("receiptCode") receiptCode: String,
    ): ApiResponse<UploadFileResponse> {
        val fileName = FileNaming.admissionTicketFileName(receiptCode, file.requireExtension(CATEGORY))
        val saved = file.inputStream.use {
            uploadFileUseCase.upload(file.toUploadCommand(CATEGORY, fileName), it)
        }
        return ApiResponse.success(UploadFileResponse.from(saved))
    }

    @GetMapping("/download")
    fun download(
        @RequestParam("receiptCode") receiptCode: String,
        @RequestParam("format", defaultValue = "pdf") format: String,
    ): ApiResponse<DownloadUrlResponse> {
        val extension = FileExtension.fromExtension(format)?.takeIf(CATEGORY::supports)
            ?: throw InvalidFileFormatException(format, CATEGORY)
        val fileName = FileNaming.admissionTicketFileName(receiptCode, extension)
        return ApiResponse.success(
            DownloadUrlResponse.from(
                issueDownloadUrlUseCase.issueByCommand(IssueDownloadUrlCommand(CATEGORY, fileName))
            )
        )
    }
}
