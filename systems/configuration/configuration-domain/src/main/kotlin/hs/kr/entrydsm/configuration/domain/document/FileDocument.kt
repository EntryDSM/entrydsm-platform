package hs.kr.entrydsm.configuration.domain.document

import java.time.Instant

data class FileDocument(
    val id: Long? = null,
    val originalName: String,
    val objectKey: String,
    val bucket: String,
    val contentType: String,
    val sizeBytes: Long,
    val checksum: String,
    val createdAt: Instant? = null,
) {
    val fileName: String
        get() = objectKey.substringAfterLast('/')
}
