package hs.kr.entrydsm.configuration.adapterout.entity

import hs.kr.entrydsm.configuration.domain.document.FileDocument
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "files")
class FileDocumentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "original_name", nullable = false, length = 255)
    val originalName: String,

    @Column(name = "object_key", unique = true, nullable = false, length = 255)
    val objectKey: String,

    @Column(name = "bucket", nullable = false, length = 100)
    val bucket: String,

    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "checksum", nullable = false, length = 64)
    val checksum: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    fun toDomain() = FileDocument(
        id = id,
        originalName = originalName,
        objectKey = objectKey,
        bucket = bucket,
        contentType = contentType,
        sizeBytes = sizeBytes,
        checksum = checksum,
        createdAt = createdAt,
    )

    companion object {
        fun from(domain: FileDocument) = FileDocumentJpaEntity(
            id = domain.id,
            originalName = domain.originalName,
            objectKey = domain.objectKey,
            bucket = domain.bucket,
            contentType = domain.contentType,
            sizeBytes = domain.sizeBytes,
            checksum = domain.checksum,
            createdAt = domain.createdAt ?: Instant.now(),
        )
    }
}
