package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.lang.reflect.Proxy

class S3StorageAdapterTest {

    @Test
    fun `객체가 있으면 존재한다고 판단한다`() {
        assertTrue(adapter(FakeS3Client()).exists("photo/a.jpg"))
    }

    @Test
    fun `본문 없는 404 응답도 객체 없음으로 처리한다`() {
        val client = FakeS3Client(headFailure = s3Exception(404))

        assertFalse(adapter(client).exists("photo/a.jpg"))
    }

    @Test
    fun `NoSuchKey 예외도 객체 없음으로 처리한다`() {
        val client = FakeS3Client(headFailure = NoSuchKeyException.builder().statusCode(404).build())

        assertFalse(adapter(client).exists("photo/a.jpg"))
    }

    @Test(expected = StorageUnavailableException::class)
    fun `403 응답은 스토리지 오류로 올린다`() {
        adapter(FakeS3Client(headFailure = s3Exception(403))).exists("photo/a.jpg")
    }

    @Test(expected = StorageUnavailableException::class)
    fun `네트워크 오류는 스토리지 오류로 올린다`() {
        val client = FakeS3Client(headFailure = SdkClientException.builder().message("connect timed out").build())

        adapter(client).exists("photo/a.jpg")
    }

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
        private val headFailure: RuntimeException? = null,
        private val deleteFailure: RuntimeException? = null,
    ) : S3Client {
        override fun serviceName(): String = "s3"

        override fun close() = Unit

        override fun headObject(request: HeadObjectRequest): HeadObjectResponse {
            headFailure?.let { throw it }
            return HeadObjectResponse.builder().build()
        }

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse {
            deleteFailure?.let { throw it }
            return DeleteObjectResponse.builder().build()
        }
    }
}
