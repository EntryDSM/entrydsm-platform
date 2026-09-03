package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.StoredObject
import java.io.InputStream

interface StoragePort {
    fun upload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        content: InputStream,
    ): StoredObject

    fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String

    fun exists(objectKey: String): Boolean

    fun delete(objectKey: String)
}
