package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.UploadFileResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate

private val CATEGORY = FileCategory.APPLICANT_LIST

@RestController
@RequestMapping("/api/document/v11/applicant-list")
class ApplicantListController(
    private val uploadFileUseCase: UploadFileUseCase,
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
) {

    @PostMapping
    fun save(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("fileName", required = false) fileName: String?,
    ): ApiResponse<UploadFileResponse> {
        file.requireExtension(CATEGORY)
        val targetFileName = fileName?.also(::requireXlsx)
            ?: FileNaming.applicantListFileName(LocalDate.now())
        val saved = file.inputStream.use {
            uploadFileUseCase.upload(file.toUploadCommand(CATEGORY, targetFileName), it)
        }
        return ApiResponse.success(UploadFileResponse.from(saved))
    }

    @GetMapping("/download")
    fun download(@RequestParam("fileName") fileName: String): ApiResponse<DownloadUrlResponse> =
        ApiResponse.success(
            DownloadUrlResponse.from(
                issueDownloadUrlUseCase.issueByCommand(IssueDownloadUrlCommand(CATEGORY, fileName))
            )
        )

    private fun requireXlsx(fileName: String) {
        if (FileExtension.fromFileName(fileName) != FileExtension.XLSX) {
            throw IllegalArgumentException("fileName must end with .xlsx: $fileName")
        }
    }
}
