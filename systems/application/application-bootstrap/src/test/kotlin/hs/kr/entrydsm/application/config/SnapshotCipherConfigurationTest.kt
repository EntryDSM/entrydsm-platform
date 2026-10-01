package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.common.crypto.SnapshotCipher
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.springframework.beans.factory.BeanCreationException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class SnapshotCipherConfigurationTest {
    private val current = Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
    private val previous = Base64.getEncoder().encodeToString(ByteArray(32) { 7 })

    @Test
    fun `Spring 설정으로 현재 키 이전 키와 legacy 키를 주입한다`() {
        AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(MapPropertySource("snapshot-test", mapOf(
                "security.snapshot.current-key-id" to "new",
                "security.snapshot.current-key-base64" to current,
                "security.snapshot.previous-keys.old" to previous,
                "security.snapshot.legacy-key-base64" to previous,
            )))
            context.register(SnapshotCipherConfiguration::class.java)
            context.refresh()
            val cipher = context.getBean(SnapshotCipher::class.java)
            val plaintext = "개인정보".toByteArray()
            assertArrayEquals(plaintext, cipher.decrypt(SnapshotCipher("old", mapOf("old" to previous)).encrypt(plaintext)))
            assertArrayEquals(plaintext, SnapshotCipher("new", mapOf("new" to current)).decrypt(cipher.encrypt(plaintext)))
            assertEquals(previous, context.getBean(SnapshotCipherProperties::class.java).legacyKeyBase64)
        }
    }

    @Test
    fun `원본 개인정보 키만 있거나 현재 키 ID가 중복되면 초기화에 실패한다`() {
        for (properties in listOf(
            mapOf("security.pii.encryption-key-base64" to previous),
            mapOf(
                "security.snapshot.current-key-id" to "new",
                "security.snapshot.current-key-base64" to current,
                "security.snapshot.previous-keys.new" to previous,
            ),
        )) {
            AnnotationConfigApplicationContext().use { context ->
                context.environment.propertySources.addFirst(MapPropertySource("snapshot-test", properties))
                context.register(SnapshotCipherConfiguration::class.java)
                assertThrows(BeanCreationException::class.java) { context.refresh() }
            }
        }
    }
}
