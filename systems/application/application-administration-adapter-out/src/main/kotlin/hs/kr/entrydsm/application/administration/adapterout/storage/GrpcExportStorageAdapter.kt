package hs.kr.entrydsm.application.administration.adapterout.storage

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.application.administration.adapterout.grpc.FileStorageClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** 기존 export_job의 버킷과 객체 경로를 그대로 파일 서비스에 전달한다. */
@Component
@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class GrpcExportStorageAdapter(
    private val files: FileStorageClient,
    @Value("\${admin.storage.bucket}") private val bucket: String,
) : StoragePort {
    override fun upload(objectKey: String, contentType: String, content: ByteArray) {
        storage { files.upload(bucket, objectKey, contentType, content) }
    }
    override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String =
        storage { files.presign(bucket, objectKey, expiresInSeconds) }
    override fun delete(objectKey: String) { storage { files.delete(bucket, objectKey) } }

    private inline fun <T> storage(action: () -> T): T = try { action() }
    catch (error: Exception) { throw AdminDomainException(ErrorCode.STORAGE_UNAVAILABLE, error) }
}
