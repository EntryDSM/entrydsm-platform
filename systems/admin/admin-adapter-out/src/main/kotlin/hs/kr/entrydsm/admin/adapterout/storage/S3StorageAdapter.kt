package hs.kr.entrydsm.admin.adapterout.storage

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest

/**
 * 산출물을 S3에 올리고 presigned URL을 발급합니다.
 */
@Component
class S3StorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${admin.storage.bucket}") private val bucket: String,
) : StoragePort {

    override fun upload(objectKey: String, contentType: String, content: ByteArray) {
        runCatching {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build(),
                RequestBody.fromBytes(content),
            )
        }.getOrElse { cause ->
            throw AdminDomainException(ErrorCode.STORAGE_UNAVAILABLE, cause)
        }
    }

    override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String =
        runCatching {
            s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                    .getObjectRequest { it.bucket(bucket).key(objectKey) }
                    .build(),
            ).url().toExternalForm()
        }.getOrElse { cause ->
            throw AdminDomainException(ErrorCode.STORAGE_UNAVAILABLE, cause)
        }

    override fun exists(objectKey: String): Boolean =
        try {
            s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(objectKey).build(),
            )
            true
        } catch (exception: NoSuchKeyException) {
            false
        }
}
