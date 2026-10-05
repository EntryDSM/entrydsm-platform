package hs.kr.entrydsm.configuration.adapterin.grpc

import com.google.protobuf.ByteString
import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.port.out.*
import hs.kr.entrydsm.configuration.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class FileStorageGrpcServiceTest {
    @Test
    fun rejectsOversizedUploadBeforeStorageAndOversizedDownload() {
        var uploads = 0
        val storage = object : ObjectStoragePort {
            override fun validate(bucket: String, objectKey: String) = Unit
            override fun upload(bucket: String, objectKey: String, contentType: String, content: ByteArray): StoredObject {
                uploads++; return StoredObject(bucket, objectKey, "checksum")
            }
            override fun download(bucket: String, objectKey: String) = ByteArray(MAX_FILE_TRANSFER_BYTES + 1)
            override fun delete(bucket: String, objectKey: String) = Unit
            override fun presign(bucket: String, objectKey: String, expiresInSeconds: Long) = "url"
        }
        val service = FileStorageGrpcService(storage, unused())
        val upload = Observer<FileObjectResponse>()
        service.uploadObject(FileObjectRequest.newBuilder().setContent(ByteString.copyFrom(ByteArray(MAX_FILE_TRANSFER_BYTES + 1))).build(), upload)
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(upload.error).code)
        assertEquals(0, uploads)
        val download = Observer<FileObjectResponse>()
        service.downloadObject(FileObjectRequest.getDefaultInstance(), download)
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(download.error).code)
        assertNull(download.value)
    }

    @Test
    fun ambiguousLookupAndInvalidPageNeverReachRepository() {
        val service = FileStorageGrpcService(unused(), unused())
        for (request in listOf(FileMetadataRequest.getDefaultInstance(),
            FileMetadataRequest.newBuilder().setPublicId("photo_test").setLegacyId(5).build())) {
            val observer = Observer<FileMetadataResponse>()
            service.findMetadata(request, observer)
            assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(observer.error).code)
        }
        val observer = Observer<FileMetadataResponse>()
        service.listMetadata(FileMetadataRequest.newBuilder().setPage(1).setSize(101).setCategory("PHOTO").build(), observer)
        assertEquals(Status.Code.INVALID_ARGUMENT, Status.fromThrowable(observer.error).code)
    }

    @Test
    fun storageFailureDoesNotExposeInternalMessage() {
        val storage = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ObjectStoragePort::class.java)) { _, _, _ ->
            throw IllegalStateException("secret bucket and credentials")
        } as ObjectStoragePort
        val observer = Observer<FileObjectResponse>()
        FileStorageGrpcService(storage, unused()).deleteObject(FileObjectRequest.getDefaultInstance(), observer)
        assertEquals(Status.Code.INTERNAL, Status.fromThrowable(observer.error).code)
        assertEquals("FILE_STORAGE_FAILED", Status.fromThrowable(observer.error).description)
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(T::class.java)) { _, _, _ -> error("검증 실패 요청은 저장소를 호출하면 안 된다") } as T

    private class Observer<T> : StreamObserver<T> {
        var value: T? = null
        var error: Throwable? = null
        override fun onNext(value: T) { this.value = value }
        override fun onError(error: Throwable) { this.error = error }
        override fun onCompleted() = Unit
    }
}
