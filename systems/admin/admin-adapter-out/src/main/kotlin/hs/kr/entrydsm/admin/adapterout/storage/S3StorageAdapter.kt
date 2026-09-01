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
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
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

    /**
     * 객체 없음(404)만 false로 봅니다. 권한 오류(403)나 통신 실패까지 없음으로 삼으면
     * 실제로는 있는 원서를 없다고 답하게 됩니다.
     */
    override fun exists(objectKey: String): Boolean =
        runCatching {
            s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(objectKey).build(),
            )
            true
        }.getOrElse { cause ->
            if (cause is S3Exception && cause.statusCode() == 404) {
                false
            } else {
                throw AdminDomainException(ErrorCode.STORAGE_UNAVAILABLE, cause)
            }
        }
}
