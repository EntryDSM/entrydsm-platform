package hs.kr.entrydsm.configuration.adapterin.document.dto

import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.FileDocument

data class UploadFileResponse(
    val key: String,
    val fileName: String,
) {
    companion object {
        fun from(fileDocument: FileDocument) = UploadFileResponse(
            key = fileDocument.objectKey,
            fileName = fileDocument.fileName,
        )
    }
}

data class UploadPhotoResponse(
    val key: String,
    val fileName: String,
    val url: String,
)

data class UploadAttachmentResponse(
    val attachmentId: String,
    val key: String,
    val fileName: String,
    val size: Long,
)

data class DownloadUrlResponse(
    val fileName: String,
    val downloadUrl: String,
    val expiresIn: Long,
) {
    companion object {
        fun from(downloadUrl: DownloadUrl) = DownloadUrlResponse(
            fileName = downloadUrl.fileName,
            downloadUrl = downloadUrl.downloadUrl,
            expiresIn = downloadUrl.expiresIn,
        )
    }
}

data class FileMetadataResponse(
    val key: String,
    val fileName: String,
    val exists: Boolean,
)
