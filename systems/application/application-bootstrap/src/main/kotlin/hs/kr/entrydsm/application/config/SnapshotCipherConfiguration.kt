package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.common.crypto.SnapshotCipher
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@ConfigurationProperties(prefix = "security.snapshot")
class SnapshotCipherProperties(
    val currentKeyId: String,
    val currentKeyBase64: String,
    val previousKeys: Map<String, String> = emptyMap(),
    val legacyKeyBase64: String? = null,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SnapshotCipherProperties::class)
class SnapshotCipherConfiguration {
    @Bean
    @Lazy(false)
    fun snapshotCipher(properties: SnapshotCipherProperties): SnapshotCipher {
        require(properties.currentKeyId !in properties.previousKeys) { "Current snapshot key ID must not be reused in previous keys" }
        return SnapshotCipher(properties.currentKeyId,
            properties.previousKeys + (properties.currentKeyId to properties.currentKeyBase64), properties.legacyKeyBase64)
    }
}
