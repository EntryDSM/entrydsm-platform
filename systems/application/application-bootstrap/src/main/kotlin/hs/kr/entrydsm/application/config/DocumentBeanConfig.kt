package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.application.administration.application.FileDocumentService
import hs.kr.entrydsm.application.administration.domain.document.port.out.*
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
class DocumentBeanConfig {
    @Bean
    fun fileDocumentService(storage: StoragePort, metadata: FileDocumentRepository, applicants: ApplicantPort,
        pdf: PdfRenderPort, forms: ApplicationFormPdfPort, tickets: AdmissionTicketSheetPort,
        @Value("\${document.presign-expiry-seconds}") expiry: Long,
        @Value("\${document.admission-year}") year: Int,
        @Value("\${admin.storage.environment}") environment: String) =
        FileDocumentService(storage, metadata, expiry, applicants, pdf, forms, tickets, year, environment)
}
