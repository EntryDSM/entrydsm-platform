package hs.kr.entrydsm.configuration

import hs.kr.entrydsm.configuration.application.FileDocumentService
import hs.kr.entrydsm.configuration.domain.document.port.out.AdmissionTicketSheetPort
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicantPort
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicationFormPdfPort
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.PdfRenderPort
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
        applicantPort: ApplicantPort,
        pdfRenderPort: PdfRenderPort,
        applicationFormPdfPort: ApplicationFormPdfPort,
        admissionTicketSheetPort: AdmissionTicketSheetPort,
        @Value("\${aws.s3.presign-expiry-seconds}") presignExpirySeconds: Long,
        @Value("\${document.admission-year}") admissionYear: Int,
        @Value("\${aws.s3.environment}") storageEnvironment: String,
    ) = FileDocumentService(
        storagePort, fileDocumentRepository, presignExpirySeconds, applicantPort, pdfRenderPort, applicationFormPdfPort,
        admissionTicketSheetPort, admissionYear, storageEnvironment,
    )
}
