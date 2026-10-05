package hs.kr.entrydsm.application.administration.adapterout.storage

import hs.kr.entrydsm.application.administration.adapterout.grpc.ConfigurationGrpcChannel
import hs.kr.entrydsm.application.administration.adapterout.grpc.FileStorageClient
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.grpc.*
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class GrpcFileMetadataAdapterTest {
    @Test fun metadataRoundTripPreservesLegacyIdOwnerTimeAndObjectPath() {
        var json: String? = null
        var lookupId = 0L
        val server = ServerBuilder.forPort(0).addService(object : FileStorageServiceGrpc.FileStorageServiceImplBase() {
            override fun saveMetadata(request: FileMetadataRequest, observer: StreamObserver<FileMetadataResponse>) {
                json = request.metadataJson
                observer.onNext(FileMetadataResponse.newBuilder().setMetadataJson(json).build()); observer.onCompleted()
            }
            override fun findMetadata(request: FileMetadataRequest, observer: StreamObserver<FileMetadataResponse>) {
                lookupId = request.legacyId
                observer.onNext(FileMetadataResponse.newBuilder().also { builder -> json?.let(builder::setMetadataJson) }.build())
                observer.onCompleted()
            }
        }).build().start()
        val channel = ConfigurationGrpcChannel("localhost", server.port, 30000)
        try {
            val adapter = GrpcFileMetadataAdapter(FileStorageClient(channel))
            assertNull(adapter.findById(42))
            val original = FileDocument(id = 42, publicId = "photo_test", originalName = "증명사진.jpg",
                objectKey = "dsm_Entry/Backend/photo/test.jpg", bucket = "old-document", contentType = "image/jpeg",
                sizeBytes = 100, checksum = "test", ownerUserId = 10, createdAt = Instant.parse("2026-09-27T23:57:30.790217Z"))
            assertEquals(original, adapter.save(original))
            assertEquals(original, adapter.findById(42))
            assertEquals(42L, lookupId)
        } finally { channel.destroy(); server.shutdownNow() }
    }
}
