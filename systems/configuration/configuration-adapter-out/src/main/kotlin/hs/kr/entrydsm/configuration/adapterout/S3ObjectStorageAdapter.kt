package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import hs.kr.entrydsm.configuration.domain.document.port.out.MAX_FILE_TRANSFER_BYTES
import hs.kr.entrydsm.configuration.domain.document.port.out.ObjectStoragePort
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

@Component
class S3ObjectStorageAdapter(
    private val client: S3Client,
    private val presigner: S3Presigner,
    @Value("\${aws.s3.internal-buckets}") buckets: String,
) : ObjectStoragePort {
    private val allowedBuckets = buckets.split(',').map(String::trim).filter(String::isNotBlank).toSet()

    override fun validate(bucket: String, objectKey: String) {
        require(bucket in allowedBuckets)
        require(objectKey.isNotBlank() && objectKey.toByteArray(Charsets.UTF_8).size <= 1024)
        require(!objectKey.startsWith('/') && objectKey.none { it.isISOControl() })
        require(objectKey.split('/').none { it == "." || it == ".." })
    }

    override fun upload(bucket: String, objectKey: String, contentType: String, content: ByteArray): StoredObject {
        validate(bucket, objectKey)
        require(content.size <= MAX_FILE_TRANSFER_BYTES && contentType.isNotBlank() && contentType.length <= 100)
        return storage("upload") {
            val result = client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                .contentType(contentType).checksumAlgorithm(ChecksumAlgorithm.SHA256).build(), RequestBody.fromBytes(content))
            StoredObject(bucket, objectKey, result.checksumSHA256() ?: result.eTag().orEmpty().trim('"'))
        }
    }

    override fun download(bucket: String, objectKey: String): ByteArray {
        validate(bucket, objectKey)
        return storage("download") {
            client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build()).use { input ->
                input.readNBytes(MAX_FILE_TRANSFER_BYTES + 1).also { require(it.size <= MAX_FILE_TRANSFER_BYTES) }
            }
        }
    }

    override fun delete(bucket: String, objectKey: String) {
        validate(bucket, objectKey)
        storage("delete") { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build()) }
    }

    override fun presign(bucket: String, objectKey: String, expiresInSeconds: Long): String {
        validate(bucket, objectKey)
        require(expiresInSeconds in 1..604800)
        return storage("presign") {
            presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build()).build()).url().toString()
        }
    }

    private inline fun <T> storage(operation: String, action: () -> T): T = try {
        action()
    } catch (error: NoSuchKeyException) {
        throw FileDocumentNotFoundException("stored-object")
    } catch (error: SdkException) {
        throw StorageUnavailableException(operation, "stored-object", error)
    }
}
