package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.AdmissionTicket
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.PdfRenderPort
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
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
private const val XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val APPLICANT_LIST_SHEET = "지원자 목록"
private const val FIRST_PASS_LIST_SHEET = "1차 합격자 명단"

/** 지원자 목록 엑셀의 열. 머리글과 값을 한 줄에 두어 순서가 어긋나지 않게 한다. */
private val APPLICANT_LIST_COLUMNS: List<Pair<String, (Applicant) -> Any?>> = listOf(
    "접수번호" to { it.receiptNumber },
    "수험번호" to { it.examineeNumber },
    "성명" to { it.name },
    "생년월일" to { it.birthDate },
    "연락처" to { it.phoneNumber },
    "지역" to { it.region?.label },
    "전형" to { it.admissionType?.label },
    "학력" to { it.graduationStatus?.label },
    "출신학교" to { it.schoolName },
    "원서 도착" to { if (it.isArrived) "도착" else "미도착" },
    "상태" to { it.status.label },
    "총점" to { it.totalScore },
)

private val FIRST_PASS_COLUMNS: List<Pair<String, (FirstPassRow) -> Any?>> = listOf(
    "전형_지역_추가" to { it.combinedCode },
    "접수번호" to { it.receiptNumber },
    "전형유형" to { it.admissionType },
    "지역" to { it.region },
    "추가유형" to { it.specialAdmissionType },
    "성명" to { it.name },
    "생년월일" to { it.birthDate },
    "주소" to { it.address },
    "전화번호" to { it.phoneNumber },
    "성별" to { it.gender },
    "학력구분" to { it.graduationStatus },
    "졸업년도" to { it.graduationYear },
    "출신학교" to { it.schoolName },
    "반" to { it.classNumber },
    "보호자 성명" to { it.guardianName },
    "보호자 전화번호" to { it.guardianPhoneNumber },
    "국어 3학년 2학기" to { it.thirdGradeSecondSemester.korean },
    "사회 3학년 2학기" to { it.thirdGradeSecondSemester.society },
    "역사 3학년 2학기" to { it.thirdGradeSecondSemester.history },
    "수학 3학년 2학기" to { it.thirdGradeSecondSemester.math },
    "과학 3학년 2학기" to { it.thirdGradeSecondSemester.science },
    "기술가정 3학년 2학기" to { it.thirdGradeSecondSemester.technology },
    "영어 3학년 2학기" to { it.thirdGradeSecondSemester.english },
    "국어 3학년 1학기" to { it.thirdGradeFirstSemester.korean },
    "사회 3학년 1학기" to { it.thirdGradeFirstSemester.society },
    "역사 3학년 1학기" to { it.thirdGradeFirstSemester.history },
    "수학 3학년 1학기" to { it.thirdGradeFirstSemester.math },
    "과학 3학년 1학기" to { it.thirdGradeFirstSemester.science },
    "기술가정 3학년 1학기" to { it.thirdGradeFirstSemester.technology },
    "영어 3학년 1학기" to { it.thirdGradeFirstSemester.english },
    "국어 직전 학기" to { it.previousSemester.korean },
    "사회 직전 학기" to { it.previousSemester.society },
    "역사 직전 학기" to { it.previousSemester.history },
    "수학 직전 학기" to { it.previousSemester.math },
    "과학 직전 학기" to { it.previousSemester.science },
    "기술가정 직전 학기" to { it.previousSemester.technology },
    "영어 직전 학기" to { it.previousSemester.english },
    "국어 직전전 학기" to { it.secondPreviousSemester.korean },
    "사회 직전전 학기" to { it.secondPreviousSemester.society },
    "역사 직전전 학기" to { it.secondPreviousSemester.history },
    "수학 직전전 학기" to { it.secondPreviousSemester.math },
    "과학 직전전 학기" to { it.secondPreviousSemester.science },
    "기술가정 직전전 학기" to { it.secondPreviousSemester.technology },
    "영어 직전전 학기" to { it.secondPreviousSemester.english },
    "3학년 성적 총합" to { it.thirdGradeTotal },
    "직전 학기 성적 총합" to { it.previousSemesterTotal },
    "직전전 학기 성적 총합" to { it.secondPreviousSemesterTotal },
    "교과성적환산점수" to { it.subjectScore },
    "봉사시간" to { it.volunteerTime },
    "봉사점수" to { it.volunteerScore },
    "결석" to { it.absentCount },
    "지각" to { it.lateCount },
    "조퇴" to { it.earlyLeaveCount },
    "결과" to { it.classAbsenceCount },
    "출석점수" to { it.attendanceScore },
    "대회" to { if (it.awarded == true) "Y" else null },
    "자격증" to { if (it.certified == true) "Y" else null },
    "가산점" to { it.additionalScore },
    "1차전형 총점" to { it.totalScore },
    "nan" to { null },
    "전형코드" to { it.admissionTypeCode },
    "지역코드" to { it.regionCode },
    "추가유형코드" to { it.specialAdmissionTypeCode },
    "검정고시 평균점" to { it.gedAverage },
)

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
    private val xlsxRenderPort: XlsxRenderPort,
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
        var current = exportJobRepository.save(job.started())

        runCatching {
            when (job.type) {
                ExportType.FIRST_PASS_LIST -> {
                    val rows = applicantRepository.findFirstPassRows()
                    current = exportJobRepository.save(current.withTotal(rows.size))
                    writeFirstPassList(current, rows) {
                        current = exportJobRepository.save(current.processed(rows.size))
                    }
                }
                else -> {
                    val applicants = applicantRepository.findAll(job.filter)
                    current = exportJobRepository.save(current.withTotal(applicants.size))
                    when (job.type) {
                        ExportType.ADMISSION_TICKET -> bundleAdmissionTickets(current, applicants)
                        ExportType.APPLICANT_LIST -> writeApplicantList(current, applicants)
                        ExportType.FIRST_PASS_LIST -> error("handled above")
                    }.also {
                        current = exportJobRepository.save(current.processed(applicants.size))
                    }
                }
            }
        }.onSuccess { objectKey ->
            val completed = exportJobRepository.save(current.completed(objectKey, Instant.now(clock)))
            if (completed.type == ExportType.FIRST_PASS_LIST) {
                deletePreviousFirstPassExports(completed)
            }
        }.onFailure { cause ->
            logger.error("Export job failed [exportJobId={}]", job.exportJobId, cause)
            exportJobRepository.save(current.failed(Instant.now(clock)))
        }
    }

    private fun deletePreviousFirstPassExports(latest: ExportJob) {
        exportJobRepository.findDownloadableByType(ExportType.FIRST_PASS_LIST)
            .filter { it.exportJobId != latest.exportJobId }
            .forEach { previous ->
                val objectKey = previous.objectKey ?: return@forEach
                runCatching {
                    storagePort.delete(objectKey)
                    exportJobRepository.save(previous.copy(objectKey = null))
                }.onFailure { cause ->
                    logger.error("Previous first-pass export deletion failed [exportJobId={}]", previous.exportJobId, cause)
                }
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

    private fun writeApplicantList(job: ExportJob, applicants: List<Applicant>): String {
        val objectKey = DocumentNaming.applicantListObjectKey(job.exportJobId)

        val xlsx = xlsxRenderPort.render(
            sheetName = APPLICANT_LIST_SHEET,
            header = APPLICANT_LIST_COLUMNS.map { (title, _) -> title },
            rows = applicants.map { applicant ->
                APPLICANT_LIST_COLUMNS.map { (_, value) -> value(applicant) }
            },
        )

        storagePort.upload(objectKey, XLSX_CONTENT_TYPE, xlsx)
        return objectKey
    }

    private fun writeFirstPassList(
        job: ExportJob,
        rows: List<FirstPassRow>,
        rendered: () -> Unit,
    ): String {
        val objectKey = DocumentNaming.firstPassListObjectKey(job.exportJobId)
        val xlsx = xlsxRenderPort.render(
            sheetName = FIRST_PASS_LIST_SHEET,
            header = FIRST_PASS_COLUMNS.map { (title, _) -> title },
            rows = rows.map { row -> FIRST_PASS_COLUMNS.map { (_, value) -> value(row) } },
        )
        rendered()
        storagePort.upload(objectKey, XLSX_CONTENT_TYPE, xlsx)
        return objectKey
    }
}
