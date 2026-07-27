package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.exception.PresignFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUploadFailedException
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.io.InputStream
import java.time.Duration

@Component
class S3StorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${aws.s3.bucket}") private val bucket: String,
) : StoragePort {

    override fun upload(
        objectKey: String,
        contentType: String,
        sizeBytes: Long,
        content: InputStream,
    ): StoredObject {
        val response = try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(sizeBytes)
                    .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                    .build(),
                RequestBody.fromInputStream(content, sizeBytes),
            )
        } catch (e: SdkException) {
            throw StorageUploadFailedException(objectKey, e)
        }
        return StoredObject(
            bucket = bucket,
            objectKey = objectKey,
            checksum = response.checksumSHA256() ?: response.eTag().orEmpty().trim('"'),
        )
    }

    override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String =
        try {
            s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                    .getObjectRequest(
                        GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build()
                    )
                    .build()
            ).url().toString()
        } catch (e: SdkException) {
            throw PresignFailedException(objectKey, e)
        }

    override fun exists(objectKey: String): Boolean =
        try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build()
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    override fun delete(objectKey: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build()
        )
    }
}
