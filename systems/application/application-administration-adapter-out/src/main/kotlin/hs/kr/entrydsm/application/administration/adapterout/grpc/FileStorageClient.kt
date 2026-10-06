package hs.kr.entrydsm.application.administration.adapterout.grpc

import com.google.protobuf.ByteString
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import hs.kr.entrydsm.configuration.domain.document.port.out.MAX_FILE_TRANSFER_BYTES
import hs.kr.entrydsm.configuration.grpc.*
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class FileStorageClient(private val grpc: ConfigurationGrpcChannel) {
    private val stub = FileStorageServiceGrpc.newBlockingStub(grpc.channel)

    fun upload(bucket: String, key: String, type: String, bytes: ByteArray): FileObjectResponse {
        require(bytes.size <= MAX_FILE_TRANSFER_BYTES)
        return call("UploadObject") { uploadObject(objectRequest(bucket, key)
            .setContentType(type).setContent(ByteString.copyFrom(bytes)).build()) }
    }

    fun download(bucket: String, key: String): ByteArray = call("DownloadObject") {
        downloadObject(objectRequest(bucket, key).build()).content.toByteArray()
            .also { require(it.size <= MAX_FILE_TRANSFER_BYTES) }
    }

    fun delete(bucket: String, key: String) {
        call("DeleteObject") { deleteObject(objectRequest(bucket, key).build()) }
    }

    fun presign(bucket: String, key: String, expiry: Long): String = call("PresignObject") {
        presignObject(objectRequest(bucket, key).setExpiresInSeconds(expiry).build()).downloadUrl
    }

    fun saveMetadata(json: String): FileMetadataResponse = call("SaveMetadata") {
        saveMetadata(FileMetadataRequest.newBuilder().setMetadataJson(json).build())
    }

    fun findMetadata(request: FileMetadataRequest): FileMetadataResponse = call("FindMetadata") { findMetadata(request) }

    fun listMetadata(category: String, page: Int, size: Int): FileMetadataResponse = call("ListMetadata") {
        listMetadata(FileMetadataRequest.newBuilder().setCategory(category).setPage(page).setSize(size).build())
    }

    fun deleteMetadata(key: String) {
        call("DeleteMetadata") { deleteMetadata(FileMetadataRequest.newBuilder().setObjectKey(key).build()) }
    }

    private fun objectRequest(bucket: String, key: String) = FileObjectRequest.newBuilder().setBucket(bucket).setObjectKey(key)

    private fun <T> call(rpc: String, action: FileStorageServiceGrpc.FileStorageServiceBlockingStub.() -> T): T = try {
        stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS).action()
    } catch (error: StatusRuntimeException) {
        if (error.status.code == Status.Code.NOT_FOUND) throw FileDocumentNotFoundException("stored-object")
        // 원격 설명과 파일 키를 로깅하거나 사용자에게 전달하지 않는다.
        throw StorageUnavailableException(rpc, "stored-object", error)
    }
}
