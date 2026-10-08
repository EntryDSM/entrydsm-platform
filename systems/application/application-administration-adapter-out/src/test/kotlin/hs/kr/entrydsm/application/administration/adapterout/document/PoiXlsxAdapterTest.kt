package hs.kr.entrydsm.application.administration.adapterout.document

import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.model.SemesterGrades
import hs.kr.entrydsm.admin.domain.model.GedScores
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.AdmissionTicketPort
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import hs.kr.entrydsm.application.administration.application.ExportJobProcessor
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoiXlsxAdapterTest {

    @Test
    fun `전형 자료 예시를 생성하고 검정고시 7과목을 숫자 셀로 출력한다`() {
        val grades = SemesterGrades("A", "B", "C", "D", "E", "A", "B")
        val applicants = listOf(
            FirstPassRow(
                receiptNumber = "0001", name = "일반지원자 예시", graduationStatus = "졸업",
                admissionType = "일반전형", region = "대전", specialAdmissionType = "해당없음",
                thirdGradeSecondSemester = grades, thirdGradeFirstSemester = grades,
                previousSemester = grades, secondPreviousSemester = grades,
                thirdGradeTotal = 48.0, previousSemesterTotal = 24.0, secondPreviousSemesterTotal = 24.0,
                subjectScore = 150.0, totalScore = 180.0,
            ),
            FirstPassRow(
                receiptNumber = "0002", name = "검정고시지원자 예시", graduationStatus = "검정고시",
                admissionType = "일반전형", region = "전국", specialAdmissionType = "해당없음",
                gedScores = GedScores(90, 91, 92, 93, 94, 95, 96), gedAverage = 93.0,
                subjectScore = 150.0, totalScore = 180.0,
            ),
            FirstPassRow(
                receiptNumber = "0003", name = "검정고시 경계값 예시", graduationStatus = "검정고시",
                gedScores = GedScores(0, 100, 80, 81, 82, 83, 85), gedAverage = 73.0,
            ),
        )
        var savedJob: ExportJob? = null
        var xlsx = byteArrayOf()
        val job = ExportJob(
            exportJobId = "exp_example", type = ExportType.ADMISSION_FILE,
            status = ExportStatus.PENDING, createdAt = Instant.EPOCH,
        )
        val processor = ExportJobProcessor(
            exportJobRepository = object : ExportJobRepository {
                override fun findByExportJobId(exportJobId: String) = savedJob
                override fun save(exportJob: ExportJob) = exportJob.also { savedJob = it }
            },
            applicantRepository = Proxy.newProxyInstance(
                javaClass.classLoader, arrayOf(ApplicantRepository::class.java),
            ) { _, method, _ ->
                check(method.name == "findAdmissionFileRows") { "예상하지 않은 호출: ${method.name}" }
                applicants
            } as ApplicantRepository,
            admissionTicketPort = object : AdmissionTicketPort {
                override fun render(tickets: List<Pair<Long, String?>>): ByteArray =
                    error("전형 자료에서 수험표를 생성하면 안 된다")
            },
            xlsxRenderPort = PoiXlsxAdapter(),
            downloadEssaysUseCase = DownloadEssaysUseCase { error("전형 자료에서 자기소개서를 생성하면 안 된다") },
            storagePort = object : StoragePort {
                override fun upload(objectKey: String, contentType: String, content: ByteArray) {
                    assertEquals("dsm_Entry/backend/stag/admission-file/admission_file_exp_example.xlsx", objectKey)
                    assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", contentType)
                    xlsx = content
                }
                override fun delete(objectKey: String) = error("이전 예시 파일이 없다")
                override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long) = error("다운로드 URL을 요청하면 안 된다")
            },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        processor.processNow(job)

        assertEquals(ExportStatus.COMPLETED, savedJob?.status)
        assertEquals(3, savedJob?.processedCount)
        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
            val sheet = workbook.getSheet("전형 자료")
            assertEquals(3, sheet.lastRowNum)
            val subjects = listOf("국어", "사회", "역사", "수학", "과학", "기술가정", "영어")
            subjects.forEachIndexed { index, subject ->
                val column = 16 + index
                assertEquals("$subject 3학년 2학기", sheet.getRow(0).getCell(column).stringCellValue)
                assertEquals(CellType.STRING, sheet.getRow(1).getCell(column).cellType)
                assertEquals(listOf("A", "B", "C", "D", "E", "A", "B")[index], sheet.getRow(1).getCell(column).stringCellValue)
                assertEquals(CellType.NUMERIC, sheet.getRow(2).getCell(column).cellType)
                assertEquals(90.0 + index, sheet.getRow(2).getCell(column).numericCellValue, 0.0)
            }
            assertEquals(0.0, sheet.getRow(3).getCell(16).numericCellValue, 0.0)
            assertEquals(100.0, sheet.getRow(3).getCell(17).numericCellValue, 0.0)
            (23..43).forEach { assertNull(sheet.getRow(2).getCell(it)) }
            assertEquals(93.0, sheet.getRow(2).getCell(63).numericCellValue, 0.0)
            assertEquals("0002", sheet.getRow(2).getCell(1).stringCellValue)
        }
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let {
            File(it, "전형 자료-예시.xlsx").writeBytes(xlsx)
        }
    }

    @Test
    fun `숫자는 숫자 칸, null 은 빈 칸, 수식처럼 보이는 글자는 글자 칸으로 쓴다`() {
        val xlsx = PoiXlsxAdapter().render(
            sheetName = "지원자 목록",
            header = listOf("접수번호", "성명", "총점"),
            rows = listOf(listOf(7, "=HYPERLINK(\"http://x\")", null)),
        )

        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
            val sheet = workbook.getSheet("지원자 목록")
            assertEquals("접수번호", sheet.getRow(0).getCell(0).stringCellValue)

            val row = sheet.getRow(1)
            assertEquals(CellType.NUMERIC, row.getCell(0).cellType)
            assertEquals(7.0, row.getCell(0).numericCellValue, 0.0)
            assertEquals(CellType.STRING, row.getCell(1).cellType)
            assertEquals("=HYPERLINK(\"http://x\")", row.getCell(1).stringCellValue)
            assertNull(row.getCell(2))
        }
    }

    @Test
    fun `지원자 점검표는 지원자마다 20행 양식으로 쓴다`() {
        val xlsx = PoiXlsxAdapter().renderApplicationChecklist(
            listOf(
                FirstPassRow(
                    receiptNumber = "0001",
                    schoolName = "대전한빛중학교",
                    graduationStatus = "졸업예정",
                    graduationYear = "2027",
                    studentNumber = "30512",
                    thirdGradeFirstSemester = SemesterGrades(korean = "A"),
                    subjectScore = 75.428,
                    totalScore = 165.0,
                ),
            ),
        )
        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
            val sheet = workbook.getSheet("지원자 점검표")
            assertEquals(19, sheet.lastRowNum)
            assertEquals(7, sheet.numMergedRegions)
            assertEquals(24.toShort(), sheet.getRow(1).getCell(2).cellStyle.index)
            assertEquals(1.0, sheet.getRow(1).getCell(2).numericCellValue, 0.0)
            assertEquals("대전한빛중학교", sheet.getRow(1).getCell(3).stringCellValue)
            assertEquals("졸업예정자", sheet.getRow(1).getCell(6).stringCellValue)
            assertEquals("30512", sheet.getRow(3).getCell(6).stringCellValue)
            assertEquals("A", sheet.getRow(11).getCell(3).stringCellValue)
            assertEquals(75.428, sheet.getRow(18).getCell(7).numericCellValue, 0.0)
            assertEquals(165.0, sheet.getRow(19).getCell(7).numericCellValue, 0.0)
        }
    }

    @Test
    fun `템플릿 인원보다 많으면 첫 양식을 복제한다`() {
        val xlsx = PoiXlsxAdapter().renderApplicationChecklist(List(10) { FirstPassRow("${it + 1}") })
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let {
            File(it, "지원자 점검표-예시.xlsx").writeBytes(xlsx)
        }

        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
            val sheet = workbook.getSheet("지원자 점검표")
            assertEquals(199, sheet.lastRowNum)
            assertEquals(70, sheet.numMergedRegions)
            assertEquals(10.0, sheet.getRow(181).getCell(2).numericCellValue, 0.0)
        }
    }
}
