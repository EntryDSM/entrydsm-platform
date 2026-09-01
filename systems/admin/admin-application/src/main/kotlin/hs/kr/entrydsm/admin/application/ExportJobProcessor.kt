package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.AdmissionTicket
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.PdfRenderPort
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private const val ZIP_CONTENT_TYPE = "application/zip"
private const val CSV_CONTENT_TYPE = "text/csv"

/**
 * 내보내기 산출물을 실제로 만들어 저장소에 올립니다.
 *
 * `@Async`는 프록시를 통해서만 동작하므로 작업 생성 서비스와 별도 빈으로 둡니다.
 * 같은 클래스 안에서 호출하면 비동기로 돌지 않습니다.
 *
 * ponytail: 인메모리 executor라 서버가 죽으면 진행 중 작업이 유실된다. 재시도가
 * 필요해지면 DB 큐나 배치 스케줄러로 승격한다.
 */
@Component
class ExportJobProcessor(
    private val exportJobRepository: ExportJobRepository,
    private val applicantRepository: ApplicantRepository,
    private val pdfRenderPort: PdfRenderPort,
    private val storagePort: StoragePort,
    private val clock: Clock,
    @Value("\${admin.admission-year}") private val admissionYear: Int,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 작업 생성 트랜잭션이 커밋된 뒤에 실행합니다. 커밋 전에 다른 스레드가 같은 행을 건드리면
     * 아직 보이지 않는 행을 갱신하려다 실패합니다.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onExportJobCreated(event: ExportJobCreatedEvent) {
        process(event.job)
    }

    private fun process(job: ExportJob) {
        exportJobRepository.save(job.started())

        runCatching {
            val applicants = applicantRepository.findAll(job.filter)
            when (job.type) {
                ExportType.ADMISSION_TICKET -> bundleAdmissionTickets(job, applicants)
                ExportType.APPLICANT_LIST -> writeApplicantList(job, applicants)
            }
        }.onSuccess { objectKey ->
            exportJobRepository.save(job.completed(objectKey, Instant.now(clock)))
        }.onFailure { cause ->
            logger.error("Export job failed [exportJobId={}]", job.exportJobId, cause)
            exportJobRepository.save(job.failed(Instant.now(clock)))
        }
    }

    /**
     * ponytail: ZIP 전체를 힙에 올린다. 한 회차 수천 명 규모면 감당되지만, 규모가 커지면
     * StoragePort에 스트림 업로드를 더하고 임시 파일로 흘려보낸다.
     */
    private fun bundleAdmissionTickets(job: ExportJob, applicants: List<Applicant>): String {
        val objectKey = DocumentNaming.admissionTicketBundleObjectKey(job.exportJobId)

        val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                applicants.forEach { applicant ->
                    val pdf = pdfRenderPort.render(
                        AdmissionTicketHtml.render(AdmissionTicket.of(applicant, admissionYear)),
                    )
                    zip.putNextEntry(ZipEntry("admission_ticket_${applicant.receiptNumber}.pdf"))
                    zip.write(pdf)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        storagePort.upload(objectKey, ZIP_CONTENT_TYPE, archive)
        return objectKey
    }

    /**
     * ponytail: 지원자 목록을 CSV로 낸다. 엑셀 서식이 필요해지면 그때 POI를 붙인다.
     */
    private fun writeApplicantList(job: ExportJob, applicants: List<Applicant>): String {
        val objectKey = DocumentNaming.applicantListObjectKey(job.exportJobId)

        val csv = buildString {
            appendLine("접수번호,수험번호,성명,지역,전형,학력,원서도착,상태,총점")
            applicants.forEach { applicant ->
                appendLine(
                    listOf(
                        applicant.receiptNumber,
                        applicant.examineeNumber ?: "",
                        applicant.name,
                        applicant.region.label,
                        applicant.admissionType.label,
                        applicant.graduationStatus.label,
                        if (applicant.isSubmitted) "도착" else "미도착",
                        applicant.status.name,
                        applicant.score?.totalScore ?: "",
                    ).joinToString(",", transform = ::toCsvField),
                )
            }
        }

        storagePort.upload(objectKey, CSV_CONTENT_TYPE, csv.toByteArray(Charsets.UTF_8))
        return objectKey
    }
}

/**
 * CSV 한 칸을 만듭니다.
 *
 * 항상 큰따옴표로 감싸 쉼표·줄바꿈·따옴표를 그대로 보존하고, 스프레드시트가 수식으로
 * 해석하는 선두 문자는 작은따옴표를 앞에 붙여 무력화합니다.
 */
fun toCsvField(value: Any?): String {
    val raw = value?.toString().orEmpty()
    val safe = if (raw.firstOrNull() in setOf('=', '+', '-', '@')) "'$raw" else raw
    return "\"" + safe.replace("\"", "\"\"") + "\""
}
