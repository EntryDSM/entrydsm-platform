package hs.kr.entrydsm.configuration

import hs.kr.entrydsm.configuration.application.FileDocumentService
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.apache.pdfbox.Loader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class DocumentBeanConfig {
    @Bean
    fun fileDocumentService(storage: StoragePort, metadata: FileDocumentRepository,
        @Value("\${aws.s3.presign-expiry-seconds}") expiry: Long,
        @Value("\${aws.s3.environment}") environment: String) = FileDocumentService(
        storage, metadata, expiry, { bytes ->
            runCatching { Loader.loadPDF(bytes).use { it.numberOfPages > 0 } }.getOrDefault(false)
        }, environment,
    )
}
