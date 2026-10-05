package hs.kr.entrydsm.application.administration.adapterout.storage

import hs.kr.entrydsm.application.administration.adapterout.grpc.FileStorageClient
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.grpc.FileMetadataRequest
import hs.kr.entrydsm.configuration.grpc.FileMetadataResponse
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class GrpcFileMetadataAdapter(private val files: FileStorageClient) : FileDocumentRepository {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    override fun save(fileDocument: FileDocument): FileDocument =
        requireNotNull(files.saveMetadata(mapper.writeValueAsString(fileDocument)).document())
    override fun findByObjectKey(objectKey: String): FileDocument? =
        files.findMetadata(FileMetadataRequest.newBuilder().setObjectKey(objectKey).build()).document()
    override fun findByPublicId(publicId: String): FileDocument? =
        files.findMetadata(FileMetadataRequest.newBuilder().setPublicId(publicId).build()).document()
    override fun findById(id: Long): FileDocument? =
        files.findMetadata(FileMetadataRequest.newBuilder().setLegacyId(id).build()).document()
    override fun findPage(category: FileCategory, page: Int, size: Int): List<FileDocument> =
        files.listMetadata(category.name, page, size).let { mapper.readValue(it.metadataJson, object : TypeReference<List<FileDocument>>() {}) }
    override fun count(category: FileCategory): Long = files.listMetadata(category.name, 1, 1).totalCount
    override fun deleteByObjectKey(objectKey: String) = files.deleteMetadata(objectKey)

    private fun FileMetadataResponse.document(): FileDocument? =
        if (hasMetadataJson()) mapper.readValue(metadataJson, FileDocument::class.java) else null
}
