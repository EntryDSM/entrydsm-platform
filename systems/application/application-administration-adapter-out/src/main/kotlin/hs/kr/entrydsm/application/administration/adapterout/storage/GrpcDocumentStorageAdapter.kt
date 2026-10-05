package hs.kr.entrydsm.application.administration.adapterout.storage

import hs.kr.entrydsm.application.administration.adapterout.grpc.FileStorageClient
import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.port.out.MAX_FILE_TRANSFER_BYTES
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import java.io.InputStream
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class GrpcDocumentStorageAdapter(
    private val files: FileStorageClient,
    @Value("\${document.storage.bucket}") private val bucket: String,
) : StoragePort {
    override fun upload(objectKey: String, contentType: String, sizeBytes: Long, content: InputStream): StoredObject {
        require(sizeBytes in 0..MAX_FILE_TRANSFER_BYTES.toLong())
        val bytes = content.readNBytes(MAX_FILE_TRANSFER_BYTES + 1)
        require(bytes.size.toLong() == sizeBytes)
        return StoredObject(bucket, objectKey, files.upload(bucket, objectKey, contentType, bytes).checksum)
    }
    override fun download(objectKey: String): ByteArray = files.download(bucket, objectKey)
    override fun delete(objectKey: String) = files.delete(bucket, objectKey)
    override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String = files.presign(bucket, objectKey, expiresInSeconds)
}
