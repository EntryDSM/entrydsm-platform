package hs.kr.entrydsm.configuration

import hs.kr.entrydsm.configuration.application.FileDocumentService
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DocumentBeanConfig {

    @Bean
    fun fileDocumentService(
        storagePort: StoragePort,
        fileDocumentRepository: FileDocumentRepository,
        @Value("\${aws.s3.presign-expiry-seconds}") presignExpirySeconds: Long,
    ) = FileDocumentService(storagePort, fileDocumentRepository, presignExpirySeconds)
}
