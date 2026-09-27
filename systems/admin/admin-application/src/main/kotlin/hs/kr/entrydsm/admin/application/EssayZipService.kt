package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import java.io.OutputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.springframework.stereotype.Service

@Service
class EssayPdfService(
    private val applicantRepository: ApplicantRepository,
    private val applicationEssayPort: ApplicationEssayPort,
) : DownloadEssaysUseCase {
    override fun writeTo(output: OutputStream): Int {
        var count = 0
        PDDocument().use { merged ->
            applicantRepository.findAll(ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS))).forEach { applicant ->
                count++
                val examineeNumber = checkNotNull(applicant.examineeNumber) { "1차 합격자의 수험 번호가 없습니다: ${applicant.id}" }
                val pdfs = applicationEssayPort.render(applicant.id, examineeNumber)
                (pdfs.introduction ?: pdfs.studyPlan)?.let { pdf ->
                    Loader.loadPDF(pdf).use { source -> source.pages.forEach(merged::importPage) }
                }
            }
            merged.save(output)
        }
        return count
    }
}
