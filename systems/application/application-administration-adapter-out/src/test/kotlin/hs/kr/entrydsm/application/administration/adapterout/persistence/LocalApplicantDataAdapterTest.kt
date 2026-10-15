package hs.kr.entrydsm.application.administration.adapterout.persistence

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.application.administration.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.application.adapterout.repository.ApplicantJpaRepository
import hs.kr.entrydsm.application.adapterout.repository.ScreeningResultEventHandler
import hs.kr.entrydsm.application.application.exception.EvaluationValidationException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import java.lang.reflect.Proxy
import java.util.Optional
import org.junit.Assert.*
import org.junit.Test

class LocalApplicantDataAdapterTest {
    @Test
    fun absentApplicantReturnsNullWithoutReadingForm() {
        val adapter = adapter(null) { error("없는 지원자의 성적을 조회하면 안 된다") }
        assertNull(adapter.findDetailById(5))
        assertNull(adapter.findDetailById(0))
    }

    @Test
    fun originalApplicantIsReturnedWithoutProjection() {
        val adapter = adapter(applicant()) { form() }
        assertEquals(5L, adapter.findDetailById(5)?.applicant?.id)
        assertEquals(listOf(5L), adapter.findAll(ApplicantFilter()).map { it.id })
        assertEquals("0005", adapter.findAdmissionFileRows().single().receiptNumber)
    }

    @Test
    fun gedScoresAreIncludedOnlyForGedApplicants() {
        val scores = hs.kr.entrydsm.application.domain.model.GedScores(
            koreanScore = 0, societyScore = 81, historyScore = 82, mathScore = 83,
            scienceScore = 84, technologyScore = 85, englishScore = 100,
        )
        val gedForm = form().copy(
            graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GED,
            gedScores = scores,
        )
        val row = adapter(applicant()) { gedForm }.findAdmissionFileRows().single()
        assertEquals(hs.kr.entrydsm.admin.domain.model.GedScores(0, 81, 82, 83, 84, 85, 100), row.gedScores)
        assertNull(adapter(applicant()) {
            gedForm.copy(graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GRADUATED)
        }.findAdmissionFileRows().single().gedScores)
    }

    @Test
    fun scoreFailureRemainsFailureAndNeverBecomesMissingApplicant() {
        val adapter = adapter(applicant()) { throw EvaluationValidationException("성적 누락") }
        val error = assertThrows(AdminDomainException::class.java) { adapter.findDetailById(5) }
        assertEquals(ErrorCode.APPLICATION_SCORE_INVALID, error.errorCode)
        assertEquals(502, error.errorCode.status)
        assertEquals(listOf(5L), error.targetIds)
        val exportError = assertThrows(AdminDomainException::class.java) { adapter.findAdmissionFileRows() }
        assertEquals(1, exportError.failedCount)
        assertEquals(1, exportError.totalCount)
        assertEquals(listOf(5L), exportError.targetIds)
    }

    @Test
    fun missingOrMismatchedFormFailsInsteadOfExportingPartialData() {
        assertEquals(ErrorCode.APPLICATION_SCORE_INVALID,
            assertThrows(AdminDomainException::class.java) {
                adapter(applicant()) { form().copy(score = null) }.findDetailById(5)
            }.errorCode)
        assertEquals(ErrorCode.APPLICATION_FORM_NOT_FOUND,
            assertThrows(AdminDomainException::class.java) {
                adapter(applicant()) { null }.findDetailById(5)
            }.errorCode)
        assertEquals(ErrorCode.APPLICATION_FORM_INVALID,
            assertThrows(AdminDomainException::class.java) {
                adapter(applicant()) { form().copy(applicantId = 6) }.findDetailById(5)
            }.errorCode)
    }

    @Test
    fun `실제 성적 계산부터 두 XLSX 출력까지 학기 배점을 유지한다`() {
        val cases = listOf(
            Triple("졸업예정 X 제외 예시", listOf("ABAAAXX", "ABBEAXX", "ACCDAXX"), hs.kr.entrydsm.application.domain.enum.GraduationType.PROSPECTIVE),
            Triple("3-1만 존재", listOf("AAAAAAA", "XXXXXXX", "XXXXXXX"), hs.kr.entrydsm.application.domain.enum.GraduationType.PROSPECTIVE),
            Triple("졸업 최대점", listOf("AAAAAAA", "AAAAAAA", "AAAAAAA"), hs.kr.entrydsm.application.domain.enum.GraduationType.GRADUATED),
            Triple("특별전형 X 제외 예시", listOf("ABAAAXX", "ABBEAXX", "ACCDAXX"), hs.kr.entrydsm.application.domain.enum.GraduationType.PROSPECTIVE),
        )
        val rows = cases.mapIndexed { index, (name, grades, graduation) ->
            val admissionType = if (index == 3) hs.kr.entrydsm.application.domain.enum.AdmissionType.SOCIAL else hs.kr.entrydsm.application.domain.enum.AdmissionType.REGULAR
            val first = subjectGrades(grades[0])
            val previous = subjectGrades(grades[1])
            val second = subjectGrades(grades[2])
            val thirdSecond = if (graduation == hs.kr.entrydsm.application.domain.enum.GraduationType.GRADUATED) subjectGrades("AAAAAAA") else null
            val record = hs.kr.entrydsm.application.domain.model.AcademicRecord(
                volunteerTime = 15,
                subjectGrades = linkedMapOf(
                    hs.kr.entrydsm.application.domain.enum.SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to first,
                    hs.kr.entrydsm.application.domain.enum.SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to previous,
                    hs.kr.entrydsm.application.domain.enum.SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to second,
                ).also { if (thirdSecond != null) it[hs.kr.entrydsm.application.domain.enum.SchoolSemester.THIRD_GRADE_SECOND_SEMESTER] = thirdSecond },
            )
            val score = hs.kr.entrydsm.application.domain.service.ScoreCalculator().calculateBreakdown(
                hs.kr.entrydsm.application.domain.model.Applicant(id = 5, accountId = 10,
                    admissionType = admissionType,
                    graduationType = graduation, academicRecord = record),
            )
            adapter(applicant().copy(totalScore = 170.0)) {
                form().copy(name = name, admissionType = admissionType,
                    graduationType = graduation, thirdGradeFirstSemester = first,
                    thirdGradeSecondSemester = thirdSecond, previousSemester = previous,
                    secondPreviousSemester = second, academicRecord = record, score = score)
            }.findAdmissionFileRows().single().copy(receiptNumber = "000${index + 1}")
        }
        assertEquals(119.0, rows[0].subjectScore!!, 0.000001)
        assertEquals(149.0, rows[0].totalScore!!, 0.000001)
        assertEquals(70.0, rows[1].subjectScore!!, 0.000001)
        assertEquals(140.0, rows[2].subjectScore!!, 0.000001)
        assertEquals(68.0, rows[3].subjectScore!!, 0.000001)
        assertEquals(98.0, rows[3].totalScore!!, 0.000001)
        val admission = export(rows, hs.kr.entrydsm.admin.domain.enum.ExportType.ADMISSION_FILE)
        val checklist = export(rows, hs.kr.entrydsm.admin.domain.enum.ExportType.APPLICATION_CHECKLIST)
        org.apache.poi.xssf.usermodel.XSSFWorkbook(java.io.ByteArrayInputStream(admission)).use { book ->
            val sheet = book.getSheet("전형 자료")
            val row = sheet.getRow(1)
            listOf(44 to 38.4, 45 to 15.2, 46 to 14.4, 47 to 119.0, 58 to 149.0).forEach { (column, expected) ->
                assertEquals(expected, row.getCell(column).numericCellValue, 0.000001)
            }
            assertEquals("X", row.getCell(28).stringCellValue)
            assertNull(sheet.getRow(2).getCell(45))
            assertNull(sheet.getRow(2).getCell(46))
            assertEquals(40.0, sheet.getRow(3).getCell(44).numericCellValue, 0.000001)
        }
        org.apache.poi.xssf.usermodel.XSSFWorkbook(java.io.ByteArrayInputStream(checklist)).use { book ->
            val sheet = book.getSheet("지원자 점검표")
            listOf(3 to 38.4, 4 to 15.2, 5 to 14.4).forEach { (column, expected) ->
                assertEquals(expected, sheet.getRow(18).getCell(column).numericCellValue, 0.000001)
            }
            assertEquals(68.0, sheet.getRow(10).getCell(7).numericCellValue, 0.000001)
            assertEquals(119.0, sheet.getRow(18).getCell(7).numericCellValue, 0.000001)
            assertEquals(149.0, sheet.getRow(19).getCell(7).numericCellValue, 0.000001)
            assertEquals("X", sheet.getRow(16).getCell(3).stringCellValue)
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(38).getCell(4).cellType)
            assertEquals(40.0, sheet.getRow(30).getCell(7).numericCellValue, 0.000001)
            (2..5).forEach { assertEquals(20.0, sheet.getRow(58).getCell(it).numericCellValue, 0.000001) }
        }
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { output ->
            java.io.File(output, "전형자료-학기배점-예시.xlsx").writeBytes(admission)
            java.io.File(output, "점검표-학기배점-예시.xlsx").writeBytes(checklist)
        }
    }

    @Test
    fun `졸업자 3학년 전체 X일 때 이전 네 학기 계산을 출력 합계에도 전달한다`() {
        val record = hs.kr.entrydsm.application.domain.model.AcademicRecord(volunteerTime = 15, subjectGrades = linkedMapOf(
            hs.kr.entrydsm.application.domain.enum.SchoolSemester.THIRD_GRADE_SECOND_SEMESTER to subjectGrades("XXXXXXX"),
            hs.kr.entrydsm.application.domain.enum.SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to subjectGrades("XXXXXXX"),
            hs.kr.entrydsm.application.domain.enum.SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to subjectGrades("AAAAAAA"),
            hs.kr.entrydsm.application.domain.enum.SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to subjectGrades("AAAAAAA"),
            hs.kr.entrydsm.application.domain.enum.SchoolSemester.FIRST_GRADE_SECOND_SEMESTER to subjectGrades("AAAAAAA"),
            hs.kr.entrydsm.application.domain.enum.SchoolSemester.FIRST_GRADE_FIRST_SEMESTER to subjectGrades("AAAAAAA"),
        ))
        val score = hs.kr.entrydsm.application.domain.service.ScoreCalculator().calculateBreakdown(
            hs.kr.entrydsm.application.domain.model.Applicant(id = 5, accountId = 10,
                admissionType = hs.kr.entrydsm.application.domain.enum.AdmissionType.REGULAR,
                graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GRADUATED, academicRecord = record),
        )
        val rows = adapter(applicant()) { form().copy(
            graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GRADUATED,
            thirdGradeSecondSemester = subjectGrades("XXXXXXX"), thirdGradeFirstSemester = subjectGrades("XXXXXXX"),
            previousSemester = subjectGrades("AAAAAAA"), secondPreviousSemester = subjectGrades("AAAAAAA"),
            score = score, academicRecord = record,
        ) }.findAdmissionFileRows()
        assertEquals(80.0, rows.single().subjectBaseScore!!, 0.0)
        org.apache.poi.xssf.usermodel.XSSFWorkbook(java.io.ByteArrayInputStream(export(rows,
            hs.kr.entrydsm.admin.domain.enum.ExportType.ADMISSION_FILE))).use { book ->
            val row = book.getSheet("전형 자료").getRow(1)
            assertNull(row.getCell(44))
            assertEquals(140.0, row.getCell(47).numericCellValue, 0.0)
            assertEquals(170.0, row.getCell(58).numericCellValue, 0.0)
        }
        org.apache.poi.xssf.usermodel.XSSFWorkbook(java.io.ByteArrayInputStream(export(rows,
            hs.kr.entrydsm.admin.domain.enum.ExportType.APPLICATION_CHECKLIST))).use { book ->
            val sheet = book.getSheet("지원자 점검표")
            assertEquals(80.0, sheet.getRow(10).getCell(7).numericCellValue, 0.0)
            assertEquals(140.0, sheet.getRow(18).getCell(7).numericCellValue, 0.0)
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(18).getCell(2).cellType)
            assertEquals(org.apache.poi.ss.usermodel.CellType.BLANK, sheet.getRow(18).getCell(3).cellType)
        }
    }

    private fun subjectGrades(value: String) = hs.kr.entrydsm.application.domain.model.SubjectGrades(
        koreanGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[0].toString()),
        societyGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[1].toString()),
        historyGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[2].toString()),
        mathGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[3].toString()),
        scienceGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[4].toString()),
        technologyGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[5].toString()),
        englishGrade = hs.kr.entrydsm.application.domain.enum.SubjectGrade.valueOf(value[6].toString()),
    )

    private fun export(rows: List<hs.kr.entrydsm.admin.domain.model.FirstPassRow>, type: hs.kr.entrydsm.admin.domain.enum.ExportType): ByteArray {
        var result = byteArrayOf()
        var saved: hs.kr.entrydsm.admin.domain.model.ExportJob? = null
        hs.kr.entrydsm.application.administration.application.ExportJobProcessor(
            object : hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository {
                override fun findByExportJobId(exportJobId: String) = saved
                override fun save(exportJob: hs.kr.entrydsm.admin.domain.model.ExportJob) = exportJob.also { saved = it }
            },
            proxy(hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository::class.java) { method ->
                check(method in listOf("findAdmissionFileRows", "findApplicationChecklistRows")); rows
            },
            object : hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort {
                override fun render(tickets: List<Pair<Long, String?>>): ByteArray = error("수험표 호출 금지")
            },
            hs.kr.entrydsm.application.administration.adapterout.document.PoiXlsxAdapter(),
            hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase { error("자기소개서 호출 금지") },
            object : hs.kr.entrydsm.admin.domain.port.out.StoragePort {
                override fun upload(objectKey: String, contentType: String, content: ByteArray) { result = content }
                override fun delete(objectKey: String) = error("삭제 호출 금지")
                override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String = error("URL 호출 금지")
            },
            java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC),
        ).processNow(hs.kr.entrydsm.admin.domain.model.ExportJob(
            exportJobId = "semester_example", type = type, status = hs.kr.entrydsm.admin.domain.enum.ExportStatus.PENDING,
            createdAt = java.time.Instant.EPOCH,
        ))
        assertEquals(hs.kr.entrydsm.admin.domain.enum.ExportStatus.COMPLETED, saved?.status)
        return result
    }

    private fun adapter(applicant: ApplicantResult?, readForm: () -> ApplicationFormResult?): LocalApplicantDataAdapter {
        val application = proxy(ApplicationPort::class.java) { method -> when (method) {
            "findApplicant" -> applicant
            "listApplicants" -> listOfNotNull(applicant)
            "findApplicationForm" -> readForm()
            else -> error("예상하지 않은 호출: $method")
        } }
        val screenings = proxy(ScreeningJpaRepository::class.java) { method -> when (method) {
            "findAll" -> emptyList<Any>()
            "findById" -> Optional.empty<Any>()
            else -> error("예상하지 않은 호출: $method")
        } }
        val results = ScreeningResultEventHandler(
            proxy(ApplicantJpaRepository::class.java) { error("조회 중 원서를 변경하면 안 된다") },
            ApplicantStatusEventOutbox { error("조회 중 이벤트를 작성하면 안 된다") }, application,
        )
        return LocalApplicantDataAdapter(application, screenings, results,
            proxy(ApplicantJpaRepository::class.java) { error("조회 중 잠금을 잡으면 안 된다") })
    }

    private fun applicant() = ApplicantResult(
        applicantId = 5, accountId = 10, name = null, schoolName = null, region = null,
        admissionType = null, photoFileId = null, birthdate = null, phoneNumber = null,
        graduationType = null, totalScore = null, status = ApplicantStatus.SUBMITTED, submittedAt = null,
    )

    private fun form() = ApplicationFormResult(
        applicantId = 5, accountId = 10, status = ApplicantStatus.SUBMITTED, name = null,
        phoneNumber = null, birthdate = null, gender = null, address = null, photoFileId = null,
        region = null, admissionType = null, specialAdmissionType = SpecialAdmissionType.NONE,
        graduationType = null, graduationDate = null, guardianName = null, guardianRelation = null,
        guardianPhoneNumber = null, middleSchool = null, thirdGradeSecondSemester = null,
        thirdGradeFirstSemester = null, previousSemester = null, secondPreviousSemester = null,
        academicRecord = null, score = hs.kr.entrydsm.application.domain.service.ScoreBreakdown(1.0, 2.0, 3.0, 0.0, 6.0), introduction = null, studyPlan = null,
    )

    private fun <T> proxy(type: Class<T>, invoke: (String) -> Any?): T =
        type.cast(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> invoke(method.name) })
}
