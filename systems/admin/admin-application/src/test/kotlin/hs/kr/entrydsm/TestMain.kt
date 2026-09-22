package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.model.SemesterGrades
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantArrivalPort
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.DistancePort
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.PdfMergePort
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.springframework.context.ApplicationEventPublisher

class AdminApplicationModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun collectsCompetitionGenderAndResidenceStatistics() {
        val applicants = listOf(
            applicant(1, AdmissionType.GENERAL, Gender.MALE, Region.DAEJEON, "대전광역시 유성구"),
            applicant(2, AdmissionType.GENERAL, Gender.FEMALE, Region.NATIONWIDE, "충청남도 천안시"),
            applicant(3, AdmissionType.GENERAL, Gender.MALE, Region.DAEJEON, "세종특별자치시 한누리대로"),
        )
        val quota = AdmissionQuota(
            quotas = Region.entries.associateWith { region ->
                AdmissionType.entries.associateWith { type ->
                    if (region == Region.DAEJEON && type == AdmissionType.GENERAL) 2 else 0
                }
            },
            updatedAt = Instant.EPOCH,
            updatedBy = "test",
        )
        val service = StatisticsService(
            applicantRepository = repository(ApplicantRepository::class.java, "findAll" to applicants),
            admissionQuotaRepository = repository(AdmissionQuotaRepository::class.java, "find" to quota),
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        val result = service.collect(
            setOf(
                StatisticsMetric.COMPETITION_RATE,
                StatisticsMetric.GENDER_RATIO,
                StatisticsMetric.REGION_STATUS,
            ),
        )

        assertEquals(1.5, result.competitionRate?.get(AdmissionType.GENERAL))
        assertEquals(3L, result.genderRatio?.total)
        assertEquals(0.667, result.genderRatio?.maleRatio)
        assertEquals(mapOf(Gender.MALE to 2L, Gender.FEMALE to 1L), result.genderRatio?.byGender)
        assertEquals(mapOf("LOCAL" to 2L, "NATIONWIDE" to 1L), result.regionStatus?.byScope)
        assertEquals(1L, result.regionStatus?.byRegion?.get(ResidenceRegion.DAEJEON))
        assertEquals(1L, result.regionStatus?.byRegion?.get(ResidenceRegion.CHUNGNAM))
        assertEquals(1L, result.regionStatus?.byRegion?.get(ResidenceRegion.SEJONG))
    }

    @Test
    fun exportsAdmissionFileRowsInSpecifiedColumnOrderAndUpdatesCounts() {
        val row = FirstPassRow(
            receiptNumber = "0001",
            combinedCode = "310",
            name = "홍길동",
            thirdGradeFirstSemester = SemesterGrades(korean = "A"),
            totalScore = 99.5,
        )
        val fixture = exportFixture(type = ExportType.ADMISSION_FILE, admissionRows = listOf(row))

        fixture.processor.onExportJobCreated(ExportJobCreatedEvent(fixture.job))

        assertEquals(EXPECTED_ADMISSION_FILE_HEADERS, fixture.header)
        assertEquals("0001", fixture.rows.single()[1])
        assertEquals("홍길동", fixture.rows.single()[5])
        assertNull(fixture.rows.single()[EXPECTED_ADMISSION_FILE_HEADERS.indexOf("nan")])
        assertEquals("dsm_Entry/backend/stag/admission-file/admission_file_exp_test.xlsx", fixture.objectKey)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fixture.contentType)
        assertEquals(
            listOf(ExportStatus.PROCESSING, ExportStatus.PROCESSING, ExportStatus.PROCESSING, ExportStatus.COMPLETED),
            fixture.saved.map { it.status },
        )
        assertEquals(1, fixture.saved.last().totalCount)
        assertEquals(1, fixture.saved.last().processedCount)
    }

    @Test
    fun exportsOnlyFirstPassApplicantsWithThreeColumns() {
        val fixture = exportFixture(
            applicants = listOf(
                Applicant(id = 1L, examineeNumber = "11001", name = "홍길동", status = hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_PASS),
            ),
        )

        fixture.processor.onExportJobCreated(ExportJobCreatedEvent(fixture.job))

        assertEquals(listOf("수험번호", "접수번호", "성명"), fixture.header)
        assertEquals(listOf("11001", "0001", "홍길동"), fixture.rows.single())
    }

    @Test
    fun preservesProcessedCountWhenFirstPassUploadFails() {
        val fixture = exportFixture(applicants = listOf(Applicant(id = 1L)), failUpload = true)

        fixture.processor.onExportJobCreated(ExportJobCreatedEvent(fixture.job))

        assertEquals(ExportStatus.FAILED, fixture.saved.last().status)
        assertEquals(1, fixture.saved.last().totalCount)
        assertEquals(1, fixture.saved.last().processedCount)
    }

    @Test
    fun deletesPreviousAdmissionFileObjectAfterNewExportCompletes() {
        val previous = ExportJob(
            exportJobId = "exp_previous",
            type = ExportType.ADMISSION_FILE,
            status = ExportStatus.COMPLETED,
            objectKey = "dsm_Entry/Backend/admission-file/admission_file_exp_previous.xlsx",
            createdAt = Instant.EPOCH,
            completedAt = Instant.EPOCH,
        )
        val fixture = exportFixture(type = ExportType.ADMISSION_FILE, previous = previous)

        fixture.processor.onExportJobCreated(ExportJobCreatedEvent(fixture.job))

        assertEquals(listOf("dsm_Entry/Backend/admission-file/admission_file_exp_previous.xlsx"), fixture.deletedObjectKeys)
        assertNull(fixture.saved.last { it.exportJobId == previous.exportJobId }.objectKey)
        assertEquals("dsm_Entry/backend/stag/admission-file/admission_file_exp_test.xlsx", fixture.saved.last { it.exportJobId == fixture.job.exportJobId }.objectKey)
    }

    @Test
    fun returnsDownloadUrlAndExpiryForCompletedFirstPassExport() {
        val completed = ExportJob(
            exportJobId = "exp_test",
            type = ExportType.FIRST_PASS_LIST,
            status = ExportStatus.COMPLETED,
            objectKey = "dsm_Entry/Backend/first-pass/first_pass_exp_test.xlsx",
            totalCount = 2,
            processedCount = 2,
            createdAt = Instant.EPOCH,
            completedAt = Instant.EPOCH,
        )
        val service = ExportService(
            exportJobRepository = object : ExportJobRepository {
                override fun findByExportJobId(exportJobId: String) = completed
                override fun save(exportJob: ExportJob) = exportJob
            },
            applicantRepository = repository(ApplicantRepository::class.java, "unused" to Unit),
            applicationEventPublisher = repository(ApplicationEventPublisher::class.java, "unused" to Unit),
            storagePort = object : StoragePort {
                override fun upload(objectKey: String, contentType: String, content: ByteArray) = Unit
                override fun delete(objectKey: String) = Unit
                override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long) = "https://example.test/file"
            },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            downloadUrlExpiresInSeconds = 900,
        )

        val result = service.findById("exp_test")

        assertEquals("https://example.test/file", result.download?.downloadUrl)
        assertEquals(Instant.EPOCH.plusSeconds(900), result.download?.expiresAt)
        assertEquals(2, result.job.totalCount)
        assertEquals(2, result.job.processedCount)
    }

    private fun exportFixture(
        type: ExportType = ExportType.FIRST_PASS_LIST,
        applicants: List<Applicant> = emptyList(),
        admissionRows: List<FirstPassRow> = emptyList(),
        previous: ExportJob? = null,
        failUpload: Boolean = false,
    ): ExportFixture {
        val saved = mutableListOf<ExportJob>()
        val exportRepository = object : ExportJobRepository {
            override fun findByExportJobId(exportJobId: String): ExportJob? = saved.lastOrNull()
            override fun save(exportJob: ExportJob): ExportJob = exportJob.also(saved::add)
            override fun findDownloadableByType(type: ExportType) = listOfNotNull(previous)
        }
        var capturedHeader = emptyList<String>()
        var capturedRows = emptyList<List<Any?>>()
        var capturedObjectKey: String? = null
        var capturedContentType: String? = null
        val deletedObjectKeys = mutableListOf<String>()
        val processor = ExportJobProcessor(
            exportJobRepository = exportRepository,
            applicantRepository = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ApplicantRepository::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "syncExportProjection" -> Unit
                    "findFirstPassApplicants" -> applicants
                    "findAdmissionFileRows" -> admissionRows
                    else -> error("unexpected call: ${method.name}")
                }
            } as ApplicantRepository,
            admissionTicketPort = object : AdmissionTicketPort {
                override fun render(applicantId: Long, examineeNumber: String?) = byteArrayOf()
            },
            pdfMergePort = object : PdfMergePort {
                override fun merge(pdfs: List<ByteArray>) = byteArrayOf()
            },
            xlsxRenderPort = object : XlsxRenderPort {
                override fun render(sheetName: String, header: List<String>, rows: List<List<Any?>>): ByteArray {
                    capturedHeader = header
                    capturedRows = rows
                    return byteArrayOf(1)
                }
            },
            storagePort = object : StoragePort {
                override fun upload(objectKey: String, contentType: String, content: ByteArray) {
                    capturedObjectKey = objectKey
                    capturedContentType = contentType
                    if (failUpload) error("upload failed")
                }

                override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long) = "unused"

                override fun delete(objectKey: String) {
                    deletedObjectKeys += objectKey
                }
            },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )
        val job = ExportJob(
            exportJobId = "exp_test",
            type = type,
            status = ExportStatus.PENDING,
            createdAt = Instant.EPOCH,
        )
        return ExportFixture(
            processor = processor,
            job = job,
            saved = saved,
            headerProvider = { capturedHeader },
            rowsProvider = { capturedRows },
            objectKeyProvider = { capturedObjectKey },
            contentTypeProvider = { capturedContentType },
            deletedObjectKeys = deletedObjectKeys,
        )
    }

    private data class ExportFixture(
        val processor: ExportJobProcessor,
        val job: ExportJob,
        val saved: List<ExportJob>,
        private val headerProvider: () -> List<String>,
        private val rowsProvider: () -> List<List<Any?>>,
        private val objectKeyProvider: () -> String?,
        private val contentTypeProvider: () -> String?,
        val deletedObjectKeys: List<String>,
    ) {
        val header get() = headerProvider()
        val rows get() = rowsProvider()
        val objectKey get() = objectKeyProvider()
        val contentType get() = contentTypeProvider()
    }

    @Test
    fun doesNotSaveAnyNumberWhenOneDistanceLookupFailsAndSkipsAlreadyIssuedApplicant() {
        val applicants = listOf(
            Applicant(1L, admissionType = AdmissionType.MEISTER, region = Region.DAEJEON, address = "주소1", isArrived = true),
            Applicant(2L, admissionType = AdmissionType.MEISTER, region = Region.DAEJEON, address = "주소2", isArrived = true),
            Applicant(
                3L,
                admissionType = AdmissionType.MEISTER,
                region = Region.DAEJEON,
                address = "주소3",
                isArrived = true,
                examineeNumber = "11001",
            ),
        )
        val repository = FakeApplicantRepository(applicants)
        val requested = mutableListOf<String>()
        val service = ApplicantService(
            applicantRepository = repository,
            applicantArrivalPort = ApplicantArrivalPort { _, _ -> },
            distancePort = DistancePort { address ->
                requested += address
                if (address == "주소2") error("maps failed") else 100L
            },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        runCatching { service.issueAll() }

        assertEquals(listOf("주소1", "주소2"), requested)
        assertTrue(repository.saved.isEmpty())
    }

    private fun applicant(
        id: Long,
        type: AdmissionType,
        gender: Gender,
        region: Region,
        address: String,
    ) = Applicant(id = id, admissionType = type, gender = gender, region = region, address = address)

    private fun <T> repository(type: Class<T>, response: Pair<String, Any>): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ ->
            if (method.name == response.first) response.second else error("unexpected call: ${method.name}")
        } as T

    private class FakeApplicantRepository(private val applicants: List<Applicant>) : ApplicantRepository {
        val saved = mutableListOf<Applicant>()

        override fun search(filter: ApplicantFilter, pageRequest: PageRequest) = Page<Applicant>(emptyList(), 1, 20, 0L)
        override fun findAll(filter: ApplicantFilter) = applicants
        override fun findById(applicantId: Long) = applicants.find { it.id == applicantId }
        override fun findDetailById(applicantId: Long): ApplicantDetail? = null
        override fun save(applicant: Applicant) = applicant.also(saved::add)
        override fun saveAll(applicants: List<Applicant>) = applicants.also(saved::addAll)
    }

    private companion object {
        val EXPECTED_ADMISSION_FILE_HEADERS = "전형_지역_추가,접수번호,전형유형,지역,추가유형,성명,생년월일,주소,전화번호,성별,학력구분,졸업년도,출신학교,반,보호자 성명,보호자 전화번호,국어 3학년 2학기,사회 3학년 2학기,역사 3학년 2학기,수학 3학년 2학기,과학 3학년 2학기,기술가정 3학년 2학기,영어 3학년 2학기,국어 3학년 1학기,사회 3학년 1학기,역사 3학년 1학기,수학 3학년 1학기,과학 3학년 1학기,기술가정 3학년 1학기,영어 3학년 1학기,국어 직전 학기,사회 직전 학기,역사 직전 학기,수학 직전 학기,과학 직전 학기,기술가정 직전 학기,영어 직전 학기,국어 직전전 학기,사회 직전전 학기,역사 직전전 학기,수학 직전전 학기,과학 직전전 학기,기술가정 직전전 학기,영어 직전전 학기,3학년 성적 총합,직전 학기 성적 총합,직전전 학기 성적 총합,교과성적환산점수,봉사시간,봉사점수,결석,지각,조퇴,결과,출석점수,대회,자격증,가산점,1차전형 총점,nan,전형코드,지역코드,추가유형코드,검정고시 평균점".split(',')
    }
}
