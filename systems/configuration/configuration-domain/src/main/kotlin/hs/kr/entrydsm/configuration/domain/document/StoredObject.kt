package hs.kr.entrydsm.configuration.domain.document

data class StoredObject(
    val bucket: String,
    val objectKey: String,
    val checksum: String,
)
