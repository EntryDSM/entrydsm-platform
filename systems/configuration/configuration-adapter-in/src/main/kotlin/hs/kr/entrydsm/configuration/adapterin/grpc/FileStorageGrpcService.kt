package hs.kr.entrydsm.configuration.adapterin.grpc

import com.google.protobuf.ByteString
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.MAX_FILE_TRANSFER_BYTES
import hs.kr.entrydsm.configuration.domain.document.port.out.ObjectStoragePort
import hs.kr.entrydsm.configuration.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/** 버킷·객체·메타데이터만 처리하며 원서나 전형 결과를 조회하지 않는다. */
@Component
class FileStorageGrpcService(private val storage: ObjectStoragePort, private val metadata: FileDocumentRepository) :
    FileStorageServiceGrpc.FileStorageServiceImplBase() {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    override fun uploadObject(request: FileObjectRequest, observer: StreamObserver<FileObjectResponse>) = respond(observer) {
        require(request.content.size() <= MAX_FILE_TRANSFER_BYTES)
        val stored = storage.upload(request.bucket, request.objectKey, request.contentType, request.content.toByteArray())
        FileObjectResponse.newBuilder().setChecksum(stored.checksum).build()
    }

    override fun downloadObject(request: FileObjectRequest, observer: StreamObserver<FileObjectResponse>) = respond(observer) {
        val content = storage.download(request.bucket, request.objectKey)
        require(content.size <= MAX_FILE_TRANSFER_BYTES)
        FileObjectResponse.newBuilder().setContent(ByteString.copyFrom(content)).build()
    }

    override fun deleteObject(request: FileObjectRequest, observer: StreamObserver<FileObjectResponse>) = respond(observer) {
        storage.delete(request.bucket, request.objectKey)
        FileObjectResponse.getDefaultInstance()
    }

    override fun presignObject(request: FileObjectRequest, observer: StreamObserver<FileObjectResponse>) = respond(observer) {
        FileObjectResponse.newBuilder().setDownloadUrl(
            storage.presign(request.bucket, request.objectKey, request.expiresInSeconds)).build()
    }

    override fun saveMetadata(request: FileMetadataRequest, observer: StreamObserver<FileMetadataResponse>) = respond(observer) {
        require(request.metadataJson.length <= 16 * 1024)
        val document = mapper.readValue(request.metadataJson, FileDocument::class.java)
        storage.validate(document.bucket, document.objectKey)
        require(document.objectKey.length <= 255 && document.publicId.length in 1..64)
        require(document.originalName.length in 1..255 && document.contentType.length in 1..100)
        require(document.sizeBytes in 0..MAX_FILE_TRANSFER_BYTES.toLong() && document.checksum.length <= 64)
        document.id?.let { require(it > 0) }
        document.ownerUserId?.let { require(it > 0) }
        FileMetadataResponse.newBuilder().setMetadataJson(mapper.writeValueAsString(metadata.save(document))).build()
    }

    override fun findMetadata(request: FileMetadataRequest, observer: StreamObserver<FileMetadataResponse>) = respond(observer) {
        require(listOf(request.publicId.isNotBlank(), request.legacyId > 0, request.objectKey.isNotBlank()).count { it } == 1)
        val found = when {
            request.publicId.isNotBlank() -> { require(request.publicId.length <= 64); metadata.findByPublicId(request.publicId) }
            request.legacyId > 0 -> metadata.findById(request.legacyId)
            else -> { require(request.objectKey.length <= 255); metadata.findByObjectKey(request.objectKey) }
        }
        FileMetadataResponse.newBuilder().also { result ->
            found?.let { storage.validate(it.bucket, it.objectKey); result.setMetadataJson(mapper.writeValueAsString(it)) }
        }.build()
    }

    override fun listMetadata(request: FileMetadataRequest, observer: StreamObserver<FileMetadataResponse>) = respond(observer) {
        require(request.page >= 1 && request.size in 1..100)
        val category = FileCategory.valueOf(request.category)
        FileMetadataResponse.newBuilder().setMetadataJson(mapper.writeValueAsString(
            metadata.findPage(category, request.page, request.size))).setTotalCount(metadata.count(category)).build()
    }

    override fun deleteMetadata(request: FileMetadataRequest, observer: StreamObserver<FileMetadataResponse>) = respond(observer) {
        require(request.objectKey.isNotBlank() && request.objectKey.length <= 255)
        metadata.findByObjectKey(request.objectKey)?.let { storage.validate(it.bucket, it.objectKey) }
        metadata.deleteByObjectKey(request.objectKey)
        FileMetadataResponse.getDefaultInstance()
    }

    private fun <T> respond(observer: StreamObserver<T>, action: () -> T) {
        try {
            observer.onNext(action())
            observer.onCompleted()
        } catch (error: Exception) {
            val status = when (error) {
                is FileDocumentNotFoundException -> Status.NOT_FOUND.withDescription("FILE_NOT_FOUND")
                is StorageUnavailableException -> Status.UNAVAILABLE.withDescription("FILE_STORAGE_UNAVAILABLE")
                is IllegalArgumentException, is tools.jackson.core.JacksonException -> Status.INVALID_ARGUMENT.withDescription("FILE_INVALID_REQUEST")
                else -> Status.INTERNAL.withDescription("FILE_STORAGE_FAILED")
            }
            org.slf4j.LoggerFactory.getLogger(javaClass).error("File operation failed [status={}, exception={}]",
                status.code, error.javaClass.name)
            observer.onError(status.asRuntimeException())
        }
    }
}
