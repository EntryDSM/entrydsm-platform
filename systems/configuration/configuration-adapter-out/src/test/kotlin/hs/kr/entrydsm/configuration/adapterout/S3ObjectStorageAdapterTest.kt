package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner

class S3ObjectStorageAdapterTest {
    @Test fun bucketAndKeyGuardsRejectRequestsBeforeStorage() {
        val adapter = S3ObjectStorageAdapter(unused(), unused(), "old-document,old-admin")
        for ((bucket, key) in listOf("unknown" to "photo.jpg", "old-document" to "../secret",
            "old-document" to "/photo.jpg", "old-admin" to "a/./b", "old-admin" to "a\nb",
            "old-admin" to "가".repeat(342))) {
            assertThrows(IllegalArgumentException::class.java) { adapter.delete(bucket, key) }
        }
        adapter.validate("old-document", "dsm_Entry/Backend/photo/test.jpg")
        adapter.validate("old-admin", "stag/checklist/test.xlsx")
    }

    @Test fun legacyKeyAndBucketRemainUnchangedAndFailuresPropagate() {
        var deleted: DeleteObjectRequest? = null
        var fail = false
        val client = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(S3Client::class.java)) { _, method, args ->
            assertEquals("deleteObject", method.name)
            if (fail) throw S3Exception.builder().statusCode(500).message("private storage details").build()
            deleted = args[0] as DeleteObjectRequest
            DeleteObjectResponse.builder().build()
        } as S3Client
        val adapter = S3ObjectStorageAdapter(client, unused(), "old-document,old-admin")
        adapter.delete("old-document", "dsm_Entry/Backend/photo/test.jpg")
        assertEquals("old-document", deleted!!.bucket())
        assertEquals("dsm_Entry/Backend/photo/test.jpg", deleted!!.key())
        fail = true
        assertThrows(StorageUnavailableException::class.java) { adapter.delete("old-admin", "stag/checklist/test.xlsx") }
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(javaClass.classLoader,
        arrayOf(T::class.java)) { _, _, _ -> error("검증 실패는 S3를 호출하면 안 된다") } as T
}
