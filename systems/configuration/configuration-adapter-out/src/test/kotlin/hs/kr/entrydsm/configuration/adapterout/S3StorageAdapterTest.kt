package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import org.junit.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.lang.reflect.Proxy

class S3StorageAdapterTest {

    @Test(expected = StorageUnavailableException::class)
    fun `삭제 실패도 스토리지 오류로 올린다`() {
        val client = FakeS3Client(deleteFailure = s3Exception(500))

        adapter(client).delete("photo/a.jpg")
    }

    @Test
    fun `삭제가 성공하면 예외를 던지지 않는다`() {
        adapter(FakeS3Client()).delete("photo/a.jpg")
    }

    private fun adapter(client: S3Client) = S3StorageAdapter(client, stubPresigner(), "entrydsm")

    // presign 은 이 테스트에서 쓰지 않는다. 메서드가 7개라 프록시로 대신한다.
    private fun stubPresigner(): S3Presigner =
        Proxy.newProxyInstance(
            S3Presigner::class.java.classLoader,
            arrayOf(S3Presigner::class.java),
        ) { _, _, _ -> throw UnsupportedOperationException() } as S3Presigner

    private fun s3Exception(statusCode: Int): S3Exception =
        S3Exception.builder().statusCode(statusCode).message("status=$statusCode").build() as S3Exception

    private class FakeS3Client(
        private val deleteFailure: RuntimeException? = null,
    ) : S3Client {
        override fun serviceName(): String = "s3"

        override fun close() = Unit

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse {
            deleteFailure?.let { throw it }
            return DeleteObjectResponse.builder().build()
        }
    }
}
