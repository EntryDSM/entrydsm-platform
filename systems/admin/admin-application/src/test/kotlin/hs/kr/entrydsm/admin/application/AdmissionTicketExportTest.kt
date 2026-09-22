package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.enum.Region
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
import hs.kr.entrydsm.admin.domain.port.out.PdfMergePort
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.context.ApplicationEventPublisher

/**
 * "수험표 출력" 내보내기. 1차 합격자만, 증명사진이 든 수험표를, PDF 하나로 받는다.
 */
class AdmissionTicketExportTest {

    private val applicants = FakeApplicantRepository()
    private val jobs = FakeExportJobRepository()
    private val storage = RecordingStoragePort()
    private val events = mutableListOf<Any>()
    private val clock = Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC)
    private val exportService = ExportService(
        jobs, applicants, ApplicationEventPublisher { events += it }, storage, clock, downloadUrlExpiresInSeconds = 900,
    )

    @Test
    fun `수험표 작업은 보낸 상태 조건을 버리고 1차 합격자로 좁히되 다른 조건은 남긴다`() {
        applicants.all += applicant(1, ApplicantStatus.FIRST_PASS, "100001")

        val job = exportService.create(
            CreateExportCommand(
                ExportType.ADMISSION_TICKET,
                ApplicantFilter(regions = setOf(Region.DAEJEON), statuses = setOf(ApplicantStatus.PENDING)),
            ),
        )

        val expected = ApplicantFilter(regions = setOf(Region.DAEJEON), statuses = setOf(ApplicantStatus.FIRST_PASS))
        assertEquals(expected, job.filter)
        assertEquals(listOf(ExportJobCreatedEvent(job)), events)
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
        assertTrue(events.isEmpty())
    }

    @Test
    fun `지원자 목록 엑셀은 보낸 조건 그대로 접수하고 지원자를 미리 읽지 않는다`() {
        val filter = ApplicantFilter(statuses = setOf(ApplicantStatus.PENDING))

        val job = exportService.create(CreateExportCommand(ExportType.APPLICANT_LIST, filter))

        assertEquals(filter, job.filter)
        assertTrue(applicants.queried.isEmpty())
    }

    @Test
    fun `1차 합격자 수험표를 수험 번호 순으로 받아 PDF 하나로 올린다`() {
        applicants.all += applicant(1, ApplicantStatus.FIRST_PASS, "100002")
        applicants.all += applicant(2, ApplicantStatus.PENDING, "100003")
        applicants.all += applicant(3, ApplicantStatus.FIRST_PASS, "100001")
        // 강제 변경으로 수험 번호 없이 1차 합격이 된 지원자는 맨 뒤에 미발급으로 찍힌다.
        applicants.all += applicant(4, ApplicantStatus.FIRST_PASS, examineeNumber = null)
        val tickets = RecordingAdmissionTicketPort()

        processor(tickets).onExportJobCreated(ExportJobCreatedEvent(ticketJob()))

        assertEquals(listOf(3L to "100001", 1L to "100002", 4L to null), tickets.requested)
        val upload = storage.uploads.single()
        assertEquals("dsm_Entry/backend/stag/admission-ticket/admission_tickets_exp_1.pdf", upload.first)
        assertEquals("application/pdf", upload.second)
        assertEquals("ticket-3|ticket-1|ticket-4", upload.third)
        val finished = jobs.saved.last()
        assertEquals(ExportStatus.COMPLETED, finished.status)
        assertEquals(upload.first, finished.objectKey)
    }

    @Test
    fun `수험표를 한 장이라도 못 받으면 작업을 실패로 끝내고 아무것도 올리지 않는다`() {
        applicants.all += applicant(1, ApplicantStatus.FIRST_PASS, "100001")
        applicants.all += applicant(2, ApplicantStatus.FIRST_PASS, "100002")
        val tickets = RecordingAdmissionTicketPort(failingApplicantId = 2)

        processor(tickets).onExportJobCreated(ExportJobCreatedEvent(ticketJob()))

        assertEquals(ExportStatus.FAILED, jobs.saved.last().status)
        assertTrue(storage.uploads.isEmpty())
    }

    private fun processor(tickets: AdmissionTicketPort) = ExportJobProcessor(
        jobs, applicants, tickets,
        // 받은 순서가 보이게 이어 붙인다.
        object : PdfMergePort {
            override fun merge(pdfs: List<ByteArray>) = pdfs.joinToString("|") { String(it) }.toByteArray()
        },
        object : XlsxRenderPort {
            override fun render(sheetName: String, header: List<String>, rows: List<List<Any?>>): ByteArray =
                error("unused")
        },
        storage, clock,
    )

    private fun ticketJob() = ExportJob(
        exportJobId = "exp_1",
        type = ExportType.ADMISSION_TICKET,
        status = ExportStatus.PENDING,
        filter = ApplicantFilter().forAdmissionTickets(),
        createdAt = Instant.now(clock),
    )

    private fun applicant(id: Long, status: ApplicantStatus, examineeNumber: String?) =
        Applicant(id = id, name = "지원자$id", examineeNumber = examineeNumber, isArrived = true, status = status)

    private class RecordingAdmissionTicketPort(private val failingApplicantId: Long? = null) : AdmissionTicketPort {
        val requested = mutableListOf<Pair<Long, String?>>()

        override fun render(applicantId: Long, examineeNumber: String?): ByteArray {
            if (applicantId == failingApplicantId) throw AdminDomainException(ErrorCode.ADMISSION_TICKET_GENERATION_FAILED)
            requested += applicantId to examineeNumber
            return "ticket-$applicantId".toByteArray()
        }
    }

    /** 상태 조건만 흉내 낸다. 나머지 조건은 GrpcApplicantDataAdapterTest 가 덮는다. */
    private class FakeApplicantRepository : ApplicantRepository {
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
