package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.FileResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.PageResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.port.`in`.FileUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private const val MAX_PAGE_SIZE = 100

/**
 * 공개 ID(`photo_…`, `attachment_…`, `guideline_…`)로 찾는 증명사진·첨부·요강.
 * 누가 적재·다운로드·삭제할 수 있는지는 FileCategory 권한표가 정한다.
 */
@RestController
@RequestMapping("/api/document/v11")
class FileController(
    private val fileUseCase: FileUseCase,
) {

    @PostMapping("/photos")
    fun uploadPhoto(
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = upload(FileCategory.PHOTO, file, requester)

    @GetMapping("/photos/{photoId}")
    fun findPhoto(
        @PathVariable photoId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = find(FileCategory.PHOTO, photoId, requester)

    @PostMapping("/attachments")
    fun uploadAttachment(
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = upload(FileCategory.ATTACHMENT, file, requester)

    @GetMapping("/attachments/{attachmentId}")
    fun findAttachment(
        @PathVariable attachmentId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = find(FileCategory.ATTACHMENT, attachmentId, requester)

    @DeleteMapping("/attachments/{attachmentId}")
    fun deleteAttachment(
        @PathVariable attachmentId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = delete(FileCategory.ATTACHMENT, attachmentId, requester)

    @PostMapping("/guidelines")
    fun uploadGuideline(
        @RequestParam("file") file: MultipartFile,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = upload(FileCategory.GUIDELINE, file, requester)

    /** 최근에 올린 요강부터. 첫 항목이 현재 요강이다. */
    @GetMapping("/guidelines")
    fun findGuidelines(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<PageResponse<FileResponse>> {
        require(page >= 1 && size in 1..MAX_PAGE_SIZE) { "page must be >= 1 and size in 1..$MAX_PAGE_SIZE" }
        val found = fileUseCase.findPage(FileCategory.GUIDELINE, page, size, requester)
        return ApiResponse.success(PageResponse.of(found.items.map(FileResponse::of), page, size, found.totalElements))
    }

    @GetMapping("/guidelines/{guidelineId}")
    fun findGuideline(
        @PathVariable guidelineId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = find(FileCategory.GUIDELINE, guidelineId, requester)

    @DeleteMapping("/guidelines/{guidelineId}")
    fun deleteGuideline(
        @PathVariable guidelineId: String,
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ) = delete(FileCategory.GUIDELINE, guidelineId, requester)

    private fun upload(category: FileCategory, file: MultipartFile, requester: Requester): ApiResponse<FileResponse> {
        val uploaded = file.inputStream.use { fileUseCase.upload(file.toUploadCommand(category, requester), it) }
        return ApiResponse.success(FileResponse.of(uploaded))
    }

    private fun find(category: FileCategory, publicId: String, requester: Requester): ApiResponse<FileResponse> =
        ApiResponse.success(FileResponse.of(fileUseCase.find(category, publicId, requester)))

    private fun delete(category: FileCategory, publicId: String, requester: Requester): ResponseEntity<Unit> {
        fileUseCase.delete(category, publicId, requester)
        return ResponseEntity.noContent().build()
    }
}
