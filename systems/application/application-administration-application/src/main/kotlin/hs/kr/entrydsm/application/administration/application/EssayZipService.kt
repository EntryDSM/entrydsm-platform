package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import java.io.OutputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.springframework.stereotype.Service

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
class EssayPdfService(
    private val applicantRepository: ApplicantRepository,
    private val applicationEssayPort: ApplicationEssayPort,
) : DownloadEssaysUseCase {
    // 실제 1차 합격자의 두 서류를 수험번호 발급 여부와 무관하게 합친다.
    override fun writeTo(output: OutputStream): Int {
        val targets = applicantRepository.findAll(ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS)))
            .sortedWith(compareBy<Applicant> { it.examineeNumber.isNullOrBlank() }.thenBy { it.id })
            .map { applicant -> applicant.id to applicant.examineeNumber.orEmpty() }
        val documents = applicationEssayPort.renderBatch(targets)
        PDDocument().use { merged ->
            val merger = PDFMergerUtility()
            documents.forEach { pdfs ->
                listOfNotNull(pdfs.introduction, pdfs.studyPlan).forEach { pdf ->
                    // 원본을 닫아도 글꼴과 리소스가 유지되도록 문서 전체를 복사한다.
                    Loader.loadPDF(pdf).use { source -> merger.appendDocument(merged, source) }
                }
            }
            merged.save(output)
        }
        return targets.size
    }
}
