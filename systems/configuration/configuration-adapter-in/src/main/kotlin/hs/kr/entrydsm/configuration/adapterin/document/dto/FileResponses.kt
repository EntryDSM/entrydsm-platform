package hs.kr.entrydsm.configuration.adapterin.document.dto

import com.fasterxml.jackson.annotation.JsonInclude
import hs.kr.entrydsm.configuration.domain.document.DownloadableFile

/**
 * document API 의 파일 응답. 적재·조회가 같은 모양이다.
 * `downloadUrl` 은 `expiresIn` 초 동안 유효한 S3 서명 URL 이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class FileResponse(
    /** 증명사진·첨부·요강의 공개 ID. 원서·수험표는 지원자 ID 로 찾으므로 없다. */
    val id: String?,
    val fileName: String,
    val size: Long,
    val downloadUrl: String,
    val expiresIn: Long,
) {
    companion object {
        /** 증명사진·첨부·요강: 공개 ID 와 올린 파일명 */
        fun of(file: DownloadableFile) = FileResponse(
            id = file.document.publicId,
            fileName = file.document.originalName,
            size = file.document.sizeBytes,
            downloadUrl = file.downloadUrl,
            expiresIn = file.expiresIn,
        )

        /** 원서·수험표: 저장 파일명(application_{applicantId}.pdf 등) */
        fun ofApplicant(file: DownloadableFile) = FileResponse(
            id = null,
            fileName = file.document.fileName,
            size = file.document.sizeBytes,
            downloadUrl = file.downloadUrl,
            expiresIn = file.expiresIn,
        )
    }
}

/** 원서 조회. 아직 올리지 않았으면 404 대신 `exists: false` 만 준다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationFileResponse(
    val exists: Boolean,
    val fileName: String? = null,
    val size: Long? = null,
    val downloadUrl: String? = null,
    val expiresIn: Long? = null,
) {
    companion object {
        fun of(file: DownloadableFile?): ApplicationFileResponse =
            file?.let { FileResponse.ofApplicant(it) }
                ?.let { ApplicationFileResponse(true, it.fileName, it.size, it.downloadUrl, it.expiresIn) }
                ?: ApplicationFileResponse(exists = false)
    }
}

/** 공통 목록 응답. [page] 는 1부터 센다. */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> of(items: List<T>, page: Int, size: Int, totalElements: Long) = PageResponse(
            items = items,
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = ((totalElements + size - 1) / size).toInt(),
        )
    }
}
