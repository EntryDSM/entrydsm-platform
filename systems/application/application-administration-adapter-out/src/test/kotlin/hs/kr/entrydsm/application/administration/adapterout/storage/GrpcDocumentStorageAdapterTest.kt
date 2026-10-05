package hs.kr.entrydsm.application.administration.adapterout.storage

import hs.kr.entrydsm.application.administration.adapterout.grpc.ConfigurationGrpcChannel
import hs.kr.entrydsm.application.administration.adapterout.grpc.FileStorageClient
import hs.kr.entrydsm.configuration.domain.document.port.out.MAX_FILE_TRANSFER_BYTES
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import hs.kr.entrydsm.configuration.grpc.*
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class GrpcDocumentStorageAdapterTest {
    @Test fun bytesBucketLegacyKeyAndDeadlineArePreservedWithoutWriteRetry() {
        val uploads = AtomicInteger()
        var received: FileObjectRequest? = null
        val server = ServerBuilder.forPort(0).addService(object : FileStorageServiceGrpc.FileStorageServiceImplBase() {
            override fun uploadObject(request: FileObjectRequest, observer: StreamObserver<FileObjectResponse>) {
                uploads.incrementAndGet(); received = request
                if (request.objectKey == "fail") {
                    observer.onError(io.grpc.Status.UNAVAILABLE.asRuntimeException()); return
                }
                observer.onNext(FileObjectResponse.newBuilder().setChecksum("test-checksum").build()); observer.onCompleted()
            }
            override fun presignObject(request: FileObjectRequest, observer: StreamObserver<FileObjectResponse>) {
                // 응답하지 않는 서버에도 제한 시간이 적용되는지 확인한다.
            }
        }).build().start()
        val channel = ConfigurationGrpcChannel("localhost", server.port, 2000)
        try {
            val adapter = GrpcDocumentStorageAdapter(FileStorageClient(channel), "old-document")
            val bytes = "test-photo".toByteArray()
            val stored = adapter.upload("dsm_Entry/Backend/photo/test.jpg", "image/jpeg", bytes.size.toLong(), ByteArrayInputStream(bytes))
            assertEquals("old-document", stored.bucket)
            assertEquals("dsm_Entry/Backend/photo/test.jpg", stored.objectKey)
            assertEquals("test-checksum", stored.checksum)
            assertEquals("old-document", received!!.bucket)
            assertArrayEquals(bytes, received!!.content.toByteArray())
            assertThrows(IllegalArgumentException::class.java) {
                adapter.upload("test.jpg", "image/jpeg", MAX_FILE_TRANSFER_BYTES.toLong() + 1, ByteArrayInputStream(bytes))
            }
            assertThrows(IllegalArgumentException::class.java) {
                adapter.upload("test.jpg", "image/jpeg", 1, ByteArrayInputStream(bytes))
            }
            assertEquals(1, uploads.get())
            assertThrows(StorageUnavailableException::class.java) {
                adapter.upload("fail", "image/jpeg", bytes.size.toLong(), ByteArrayInputStream(bytes))
            }
            assertEquals(2, uploads.get())
            assertThrows(StorageUnavailableException::class.java) { adapter.issueDownloadUrl("test.jpg", 900) }
        } finally { channel.destroy(); server.shutdownNow() }
    }
}
