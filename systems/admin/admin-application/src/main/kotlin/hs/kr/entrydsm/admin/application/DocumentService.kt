package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.AdmissionTicket
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.DownloadLink
import hs.kr.entrydsm.admin.domain.port.`in`.IssueAdmissionTicketUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.IssueApplicationDocumentUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.PdfRenderPort
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import java.time.Clock
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val PDF_CONTENT_TYPE = "application/pdf"

/**
 * 수험표와 원서 원본의 다운로드 링크를 발급합니다.
 *
 * 수험표는 요청 시점에 새로 만들어 올립니다. 이름 정정이나 수험 번호 발급이 반영된
 * 최신본을 항상 내려주기 위해서입니다.
 */
@Service
@Transactional(readOnly = true)
class DocumentService(
    private val applicantRepository: ApplicantRepository,
    private val pdfRenderPort: PdfRenderPort,
    private val storagePort: StoragePort,
    private val clock: Clock,
    @Value("\${admin.admission-year}") private val admissionYear: Int,
    @Value("\${admin.storage.download-url-expires-seconds:900}")
    private val downloadUrlExpiresInSeconds: Long,
) : IssueAdmissionTicketUseCase,
    IssueApplicationDocumentUseCase {

    override fun issueAdmissionTicket(applicantId: Long): DownloadLink {
        val applicant = requireApplicant(applicantId)
        val objectKey = DocumentNaming.admissionTicketObjectKey(applicant.receiptNumber)

        val pdf = pdfRenderPort.render(
            AdmissionTicketHtml.render(AdmissionTicket.of(applicant, admissionYear)),
        )
        storagePort.upload(objectKey, PDF_CONTENT_TYPE, pdf)

        return downloadLink(objectKey)
    }

    override fun issueApplicationDocument(applicantId: Long): DownloadLink {
        val applicant = requireApplicant(applicantId)
        val objectKey = DocumentNaming.applicationDocumentObjectKey(applicant.receiptNumber)

        if (!storagePort.exists(objectKey)) {
            throw AdminDomainException(ErrorCode.APPLICATION_DOCUMENT_NOT_FOUND)
        }

        return downloadLink(objectKey)
    }

    private fun downloadLink(objectKey: String): DownloadLink = DownloadLink(
        downloadUrl = storagePort.issueDownloadUrl(objectKey, downloadUrlExpiresInSeconds),
        expiresAt = Instant.now(clock).plusSeconds(downloadUrlExpiresInSeconds),
    )

    private fun requireApplicant(applicantId: Long): Applicant =
        applicantRepository.findById(applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
}
