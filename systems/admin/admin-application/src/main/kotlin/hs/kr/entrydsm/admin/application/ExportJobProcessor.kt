package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.time.Clock
import java.time.Instant
import java.io.ByteArrayOutputStream
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

private const val XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val ZIP_CONTENT_TYPE = "application/zip"
private const val FIRST_PASS_LIST_SHEET = "1차 합격자 명단"
private const val ADMISSION_FILE_SHEET = "전형 자료"

private val FIRST_PASS_COLUMNS: List<Pair<String, (Applicant) -> Any?>> = listOf(
    "수험번호" to { it.examineeNumber },
    "접수번호" to { it.receiptNumber },
    "성명" to { it.name },
)

private val ADMISSION_FILE_COLUMNS: List<Pair<String, (FirstPassRow) -> Any?>> = listOf(
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
    private val admissionTicketPort: AdmissionTicketPort,
    private val xlsxRenderPort: XlsxRenderPort,
    private val downloadEssaysUseCase: DownloadEssaysUseCase,
    private val storagePort: StoragePort,
    private val clock: Clock,
    @Value("\${admin.storage.environment}") private val storageEnvironment: String = "stag",
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processNow(job: ExportJob) {
        process(job)
    }

    private fun process(job: ExportJob) {
        var current = exportJobRepository.save(job.started())

        runCatching {
            if (job.type in PROJECTION_EXPORT_TYPES) {
                applicantRepository.syncExportProjection()
            }
            when (job.type) {
                ExportType.FIRST_PASS -> {
                    val applicants = applicantRepository.findFirstPassApplicants()
                    current = exportJobRepository.save(current.withTotal(applicants.size))
                    writeFirstPassList(current, applicants) {
                        current = exportJobRepository.save(current.processed(applicants.size))
                    }
                }
                ExportType.ADMISSION_FILE -> {
                    val rows = applicantRepository.findAdmissionFileRows()
                    current = exportJobRepository.save(current.withTotal(rows.size))
                    writeAdmissionFile(current, rows) {
                        current = exportJobRepository.save(current.processed(rows.size))
                    }
                }
                ExportType.APPLICATION_CHECKLIST -> {
                    val rows = applicantRepository.findApplicationChecklistRows()
                    current = exportJobRepository.save(current.withTotal(rows.size))
                    val objectKey = DocumentNaming.applicationChecklistObjectKey(job.exportJobId, storageEnvironment)
                    val xlsx = xlsxRenderPort.renderApplicationChecklist(rows)
                    current = exportJobRepository.save(current.processed(rows.size))
                    storagePort.upload(objectKey, XLSX_CONTENT_TYPE, xlsx)
                    objectKey
                }
                ExportType.ESSAYS -> {
                    val objectKey = DocumentNaming.essaysObjectKey(job.exportJobId, storageEnvironment)
                    // ponytail: ZIP 전체를 메모리에 보관한다. 대용량이 되면 StoragePort에 스트리밍 업로드를 추가한다.
                    val output = ByteArrayOutputStream()
                    val count = downloadEssaysUseCase.writeTo(output)
                    current = exportJobRepository.save(current.withTotal(count).processed(count))
                    val zip = output.toByteArray()
                    storagePort.upload(objectKey, ZIP_CONTENT_TYPE, zip)
                    objectKey
                }
                ExportType.ADMISSION_TICKET -> {
                    val applicants = applicantRepository.findAll(job.filter)
                    current = exportJobRepository.save(current.withTotal(applicants.size))
                    bundleAdmissionTickets(current, applicants).also {
                        current = exportJobRepository.save(current.processed(applicants.size))
                    }
                }
            }
        }.onSuccess { objectKey ->
            val completed = exportJobRepository.save(current.completed(objectKey, Instant.now(clock)))
            if (completed.type in PROJECTION_EXPORT_TYPES) {
                deletePreviousExports(completed)
            }
        }.onFailure { cause ->
            logger.error("Export job failed [exportJobId={}]", job.exportJobId, cause)
            exportJobRepository.save(current.failed(Instant.now(clock)))
        }
    }

    private fun deletePreviousExports(latest: ExportJob) {
        exportJobRepository.findDownloadableByType(latest.type)
            .filter { it.exportJobId != latest.exportJobId }
            .forEach { previous ->
                val objectKey = previous.objectKey ?: return@forEach
                runCatching {
                    storagePort.delete(objectKey)
                    exportJobRepository.save(previous.copy(objectKey = null))
                }.onFailure { cause ->
                    logger.error("Previous export deletion failed [exportJobId={}]", previous.exportJobId, cause)
                }
            }
    }

    /**
     * 1차 합격자 수험표를 수험 번호 순으로 한 시트에 이어 그린 xlsx 하나로 올립니다. 대상은 접수할 때 1차 합격자로
     * 좁혀 둡니다. 양식과 증명사진은 document 가 넣고, 수험 번호는 admin 이 넘겨줍니다.
     */
    private fun bundleAdmissionTickets(job: ExportJob, applicants: List<Applicant>): String {
        val objectKey = DocumentNaming.admissionTicketBundleObjectKey(job.exportJobId, storageEnvironment)

        val tickets = applicants
            // 수험 번호가 없는 1차 합격자(강제 변경)는 뒤로 간다. 같은 자리끼리는 접수 번호 순 그대로다.
            .sortedWith(compareBy(nullsLast<String>()) { it.examineeNumber })
            .map { it.id to it.examineeNumber }

        storagePort.upload(objectKey, XLSX_CONTENT_TYPE, admissionTicketPort.render(tickets))
        return objectKey
    }

    private fun writeFirstPassList(
        job: ExportJob,
        applicants: List<Applicant>,
        rendered: () -> Unit,
    ): String {
        val objectKey = DocumentNaming.firstPassListObjectKey(job.exportJobId, storageEnvironment)
        val xlsx = xlsxRenderPort.render(
            sheetName = FIRST_PASS_LIST_SHEET,
            header = FIRST_PASS_COLUMNS.map { (title, _) -> title },
            rows = applicants.map { applicant -> FIRST_PASS_COLUMNS.map { (_, value) -> value(applicant) } },
        )
        rendered()
        storagePort.upload(objectKey, XLSX_CONTENT_TYPE, xlsx)
        return objectKey
    }

    private fun writeAdmissionFile(
        job: ExportJob,
        rows: List<FirstPassRow>,
        rendered: () -> Unit,
    ): String {
        val objectKey = DocumentNaming.admissionFileObjectKey(job.exportJobId, storageEnvironment)
        val xlsx = xlsxRenderPort.render(
            sheetName = ADMISSION_FILE_SHEET,
            header = ADMISSION_FILE_COLUMNS.map { (title, _) -> title },
            rows = rows.map { row -> ADMISSION_FILE_COLUMNS.map { (_, value) -> value(row) } },
        )
        rendered()
        storagePort.upload(objectKey, XLSX_CONTENT_TYPE, xlsx)
        return objectKey
    }

    private companion object {
        val PROJECTION_EXPORT_TYPES = setOf(
            ExportType.FIRST_PASS,
            ExportType.ADMISSION_FILE,
            ExportType.APPLICATION_CHECKLIST,
        )
    }
}
