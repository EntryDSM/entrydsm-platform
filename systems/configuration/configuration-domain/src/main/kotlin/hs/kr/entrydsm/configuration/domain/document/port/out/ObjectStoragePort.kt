package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.StoredObject

const val MAX_FILE_TRANSFER_BYTES = 64 * 1024 * 1024

/** 내부 호출도 버킷 허용 목록과 객체 키를 검증한다. 파일 내용은 해석하지 않는다. */
interface ObjectStoragePort {
    fun validate(bucket: String, objectKey: String)
    fun upload(bucket: String, objectKey: String, contentType: String, content: ByteArray): StoredObject
    fun download(bucket: String, objectKey: String): ByteArray
    fun delete(bucket: String, objectKey: String)
    fun presign(bucket: String, objectKey: String, expiresInSeconds: Long): String
}
