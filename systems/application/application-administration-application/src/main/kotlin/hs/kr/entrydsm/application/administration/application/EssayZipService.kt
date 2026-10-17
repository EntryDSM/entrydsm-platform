package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import hs.kr.entrydsm.admin.domain.port.out.EssayPdfMergePort
import java.io.OutputStream
import org.springframework.stereotype.Service

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
class EssayPdfService(
    private val applicantRepository: ApplicantRepository,
    private val applicationEssayPort: ApplicationEssayPort,
    private val essayPdfMergePort: EssayPdfMergePort,
) : DownloadEssaysUseCase {
    // 실제 1차 합격자의 통합 서식을 수험번호 발급 여부와 무관하게 합친다.
    override fun writeTo(output: OutputStream): Int {
        val targets = applicantRepository.findAll(ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS)))
            .sortedWith(compareBy<Applicant> { it.examineeNumber.isNullOrBlank() }.thenBy { it.id })
            .map { applicant -> applicant.id to applicant.examineeNumber?.takeIf(String::isNotBlank).orEmpty() }
        val documents = applicationEssayPort.renderBatch(targets)
        essayPdfMergePort.writeTo(documents, output)
        return targets.size
    }
}
