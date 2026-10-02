package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "수험표 출력" 내보내기. 1차 합격자만, 증명사진이 든 수험표를, xlsx 하나로 받는다.
 */
class AdmissionTicketExportTest {

    private val applicants = FakeApplicantRepository()
    private val jobs = FakeExportJobRepository()
    private val storage = RecordingStoragePort()
    private val clock = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC)
    private val exportService = ExportService(
        jobs, applicants, storage, clock, downloadUrlExpiresInSeconds = 900,
    )

    @Test
    fun `수험표 작업은 전체 1차 합격자만 대상으로 한다`() {
        applicants.all += applicant(1, ApplicantStatus.FIRST_PASS, "100001")

        val job = exportService.create(CreateExportCommand(ExportType.ADMISSION_TICKET))

        val expected = ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS))
        assertEquals(expected, job.filter)
        assertEquals(ExportStatus.PENDING, job.status)
    }

    @Test
    fun `1차 합격자가 없으면 수험표 작업을 접수하지 않고 409로 알린다`() {
        applicants.all += applicant(1, ApplicantStatus.PENDING, "100001")
        applicants.all += applicant(2, ApplicantStatus.FIRST_FAIL, "100002")
        // 2차 합격자 등록은 1차 합격자가 아니어도 최종 불합격을 붙이므로 1차 합격으로 치지 않는다.
        applicants.all += applicant(3, ApplicantStatus.FINAL_FAIL, "100003")

        val failure = assertThrows(AdminDomainException::class.java) {
            exportService.create(CreateExportCommand(ExportType.ADMISSION_TICKET))
        }

        assertEquals(ErrorCode.ADMISSION_TICKET_NO_TARGET, failure.errorCode)
        assertTrue(jobs.saved.isEmpty())
    }

    @Test
    fun `1차 합격자 수험표를 수험 번호 순으로 한 번에 받아 xlsx 하나로 올린다`() {
        applicants.all += applicant(1, ApplicantStatus.FIRST_PASS, "100002")
        applicants.all += applicant(2, ApplicantStatus.PENDING, "100003")
        applicants.all += applicant(3, ApplicantStatus.FIRST_PASS, "100001")
        // 강제 변경으로 수험 번호 없이 1차 합격이 된 지원자는 맨 뒤에 미발급으로 찍힌다.
        applicants.all += applicant(4, ApplicantStatus.FIRST_PASS, examineeNumber = null)
        val tickets = RecordingAdmissionTicketPort()

        processor(tickets).processNow(ticketJob())

        assertEquals(listOf(listOf(3L to "100001", 1L to "100002", 4L to null)), tickets.requested)
        val upload = storage.uploads.single()
        assertEquals("dsm_Entry/backend/stag/admission-ticket/admission_tickets_exp_1.xlsx", upload.first)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", upload.second)
        assertEquals("tickets-3|1|4", upload.third)
        val finished = jobs.saved.last()
        assertEquals(ExportStatus.COMPLETED, finished.status)
        assertEquals(upload.first, finished.objectKey)
    }

    @Test
    fun `document 가 수험표를 못 그리면 작업을 실패로 끝내고 아무것도 올리지 않는다`() {
        applicants.all += applicant(1, ApplicantStatus.FIRST_PASS, "100001")
        applicants.all += applicant(2, ApplicantStatus.FIRST_PASS, "100002")
        val tickets = RecordingAdmissionTicketPort(failingApplicantId = 2)

        processor(tickets).processNow(ticketJob())

        assertEquals(ExportStatus.FAILED, jobs.saved.last().status)
        assertEquals(ErrorCode.ADMISSION_TICKET_GENERATION_FAILED.name, jobs.saved.last().failureCode)
        assertEquals(ErrorCode.ADMISSION_TICKET_GENERATION_FAILED.message, jobs.saved.last().failureMessage)
        assertTrue(storage.uploads.isEmpty())
    }

    @Test
    fun `체크리스트 동기화 실패 사유와 건수를 남기고 산출물을 올리지 않는다`() {
        applicants.syncFailure = AdminDomainException(ErrorCode.APPLICATION_FORM_INVALID,
            IllegalArgumentException("개인정보 원서 본문"), failedCount = 1, totalCount = 3)
        processor(RecordingAdmissionTicketPort()).processNow(ticketJob().copy(type = ExportType.APPLICATION_CHECKLIST))
        val failed = jobs.saved.last()
        assertEquals(ExportStatus.FAILED, failed.status)
        assertEquals("APPLICATION_FORM_INVALID", failed.failureCode)
        assertEquals(ErrorCode.APPLICATION_FORM_INVALID.message, failed.failureMessage)
        assertEquals(1, failed.failedCount)
        assertEquals(3, failed.totalCount)
        assertEquals(0, failed.processedCount)
        assertTrue(storage.uploads.isEmpty())
    }

    @Test
    fun `이전 파일 목록 조회 실패가 새 체크리스트 완료를 취소하지 않는다`() {
        applicants.all += applicant(1, ApplicantStatus.PENDING, "100001")
        jobs.lookupFailure = IllegalStateException("No active transaction")
        processor(RecordingAdmissionTicketPort()).processNow(ticketJob().copy(type = ExportType.APPLICATION_CHECKLIST))
        assertEquals(ExportStatus.COMPLETED, jobs.saved.last().status)
        assertEquals(1, storage.uploads.size)
        assertEquals(1, jobs.lookupCount)
    }

    private fun processor(tickets: AdmissionTicketPort) = ExportJobProcessor(
        jobs, applicants, tickets,
        object : XlsxRenderPort {
            override fun render(sheetName: String, header: List<String>, rows: List<List<Any?>>): ByteArray =
                error("unused")
            override fun renderApplicationChecklist(rows: List<hs.kr.entrydsm.admin.domain.model.FirstPassRow>): ByteArray =
                "checklist".toByteArray()
        },
        hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase { 0 },
        storage, clock,
    )

    private fun ticketJob() = ExportJob(
        exportJobId = "exp_1",
        type = ExportType.ADMISSION_TICKET,
        status = ExportStatus.PENDING,
        filter = ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS)),
        createdAt = Instant.now(clock),
    )

    private fun applicant(id: Long, status: ApplicantStatus, examineeNumber: String?) =
        Applicant(id = id, name = "지원자$id", examineeNumber = examineeNumber, isArrived = true, status = status)

    private class RecordingAdmissionTicketPort(private val failingApplicantId: Long? = null) : AdmissionTicketPort {
        val requested = mutableListOf<List<Pair<Long, String?>>>()

        override fun render(tickets: List<Pair<Long, String?>>): ByteArray {
            if (tickets.any { (applicantId, _) -> applicantId == failingApplicantId }) {
                throw AdminDomainException(ErrorCode.ADMISSION_TICKET_GENERATION_FAILED)
            }
            requested += tickets
            // 받은 순서가 보이게 적는다.
            return "tickets-${tickets.joinToString("|") { (applicantId, _) -> "$applicantId" }}".toByteArray()
        }
    }

    /** 상태 조건만 흉내 낸다. 나머지 조건은 GrpcApplicantDataAdapterTest 가 덮는다. */
    private class FakeApplicantRepository : ApplicantRepository {
        var syncFailure: AdminDomainException? = null
        override fun syncExportProjection() { syncFailure?.let { throw it } }
        val all = mutableListOf<Applicant>()
        val queried = mutableListOf<ApplicantFilter>()

        override fun findAll(filter: ApplicantFilter): List<Applicant> {
            queried += filter
            return all.filter { filter.statuses.isEmpty() || it.status in filter.statuses }
        }

        override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> = error("unused")

        override fun findById(applicantId: Long): Applicant? = error("unused")

        override fun findDetailById(applicantId: Long): ApplicantDetail? = error("unused")

        override fun save(applicant: Applicant): Applicant = error("unused")

        override fun saveAll(applicants: List<Applicant>): List<Applicant> = error("unused")
    }

    private class FakeExportJobRepository : ExportJobRepository {
        val saved = mutableListOf<ExportJob>()
        var lookupFailure: Exception? = null
        var lookupCount = 0
        override fun findDownloadableByType(type: ExportType): List<ExportJob> {
            lookupCount++
            lookupFailure?.let { throw it }
            return emptyList()
        }

        override fun findByExportJobId(exportJobId: String): ExportJob? = saved.lastOrNull { it.exportJobId == exportJobId }

        override fun save(exportJob: ExportJob): ExportJob = exportJob.also { saved += it }
    }

    private class RecordingStoragePort : StoragePort {
        val uploads = mutableListOf<Triple<String, String, String>>()

        override fun upload(objectKey: String, contentType: String, content: ByteArray) {
            uploads += Triple(objectKey, contentType, String(content))
        }

        override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String = "https://s3/$objectKey"

        override fun delete(objectKey: String) = Unit
    }
}
