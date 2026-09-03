package hs.kr.entrydsm.admin.domain.port.out

/**
 * 산출물 파일을 보관하고 서명된 다운로드 링크를 발급하는 저장소입니다.
 */
interface StoragePort {
    fun upload(objectKey: String, contentType: String, content: ByteArray)

    fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String

    fun exists(objectKey: String): Boolean
}
