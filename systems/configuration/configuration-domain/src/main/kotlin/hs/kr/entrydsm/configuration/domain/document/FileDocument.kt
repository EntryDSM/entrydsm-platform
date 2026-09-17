package hs.kr.entrydsm.configuration.domain.document

import java.time.Instant

data class FileDocument(
    val id: Long? = null,
    /** 밖에 노출하는 ID. `{종류}_{32자 임의값}` 이라 순번을 드러내지 않는다. */
    val publicId: String,
    val originalName: String,
    val objectKey: String,
    val bucket: String,
    val contentType: String,
    val sizeBytes: Long,
    val checksum: String,
    val ownerUserId: Long? = null,
    val createdAt: Instant? = null,
) {
    val fileName: String
        get() = objectKey.substringAfterLast('/')
}
