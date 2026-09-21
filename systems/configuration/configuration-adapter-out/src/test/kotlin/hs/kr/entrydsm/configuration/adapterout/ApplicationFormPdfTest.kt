package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm
import hs.kr.entrydsm.configuration.domain.document.ApplicationFormHtml
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * 요강이 "인터넷접수 후 출력"이라고 적은 서식은 6개다. 지원자가 한 번 내려받아 그대로 인쇄해 우편으로 보내는
 * 종이라 **서식마다 정확히 A4 한 장**이어야 한다. 한 서식이 넘치면 뒤 서식이 통째로 밀리므로 쪽수를 단언한다.
 *
 * 학교장 추천서(서식 4)만 특별전형 지원자에게만 붙어 일반전형 원서는 다섯 장이다.
 */
class ApplicationFormPdfTest {

    /** 요강의 인터넷접수 후 출력 서식 수 — 입학원서·개인정보 동의·자기소개서·추천서·금연 동의·흡연검사 동의 */
    private val FORM_COUNT = 6

    /** 학교장 추천서(서식 4)는 특별전형 지원자만 내는 서식이라 일반전형 원서에는 없다. */
    private val FORM_COUNT_WITHOUT_RECOMMENDATION = FORM_COUNT - 1

    @Test
    fun `칸을 다 채운 원서를 A4 여섯 장으로 찍는다`() {
        val pdf = render(form(), photo = true)

        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")
        assertA4Pages(pdf)

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/outputs.zip 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "application-form.pdf").writeBytes(pdf) }
    }

    /**
     * 요강의 서식은 표가 페이지 아래 여백까지 찬다. 절반만 차면 서명·날인란이 좁아져 인쇄물로 쓸 수 없다.
     * 칸 높이를 줄이는 변경이 이 단언을 깨면 [ApplicationFormHtml] 의 행 높이를 다시 맞춰야 한다.
     */
    @Test
    fun `표가 A4 아래 여백까지 채운다`() {
        val bottom = lowestTextBaseline(render(form(), photo = true), page = 1)

        // A4 세로 842pt, 아래 여백 12mm(34pt). 마지막 행이 여백에서 40pt 안쪽까지는 내려와야 한다.
        assertTrue("표가 $bottom pt 에서 끝나 페이지 아래가 빈다", bottom > 842 - 34 - 40)
    }

    @Test
    fun `주소·학교명·보호자명이 길어도 한 장을 넘지 않는다`() {
        val pdf = render(
            form().copy(
                name = "황보구양선우제갈남궁",
                address = "(34503) 대전광역시 유성구 가정북로 76번길 123-45 대덕소프트웨어마이스터고등학교 기숙사 제3생활관 502호",
                guardianName = "황보구양선우제갈남궁",
                guardianRelation = "아버지의 사촌 형제",
                school = ApplicationForm.MiddleSchool(
                    name = "대전광역시립대덕소프트웨어부설중학교",
                    studentNumber = "30125",
                    phone = "042-000-0000",
                    teacherName = "황보구양선우제갈남궁",
                ),
            ),
        )

        assertA4Pages(pdf)
    }

    @Test
    fun `일반전형 원서는 학교장 추천서를 빼고 다섯 장을 찍는다`() {
        val pdf = render(form().copy(admissionType = Applicant.AdmissionType.REGULAR))

        assertA4Pages(pdf, FORM_COUNT_WITHOUT_RECOMMENDATION)
    }

    @Test
    fun `작성 중이라 전형을 고르지 않은 원서도 빈 칸인 채로 다섯 장을 찍는다`() {
        val pdf = render(
            ApplicationForm(
                applicantId = 12, userId = 10, name = "홍길동", phoneNumber = null, birthdate = null,
                gender = null, address = null, photoFileId = null, region = null, admissionType = null,
                specialNote = null, graduationType = null, graduationDate = null, guardianName = null,
                guardianRelation = null, guardianPhoneNumber = null, school = null,
                semesterGrades = emptyList(), academicRecord = null, introduction = null, studyPlan = null,
            ),
        )

        assertA4Pages(pdf, FORM_COUNT_WITHOUT_RECOMMENDATION)
    }

    private fun render(form: ApplicationForm, photo: Boolean = false): ByteArray =
        OpenHtmlToPdfAdapter().render(
            ApplicationFormHtml.render(
                admissionYear = 2027,
                form = form,
                photoDataUri = if (photo) "data:image/png;base64," + Base64.getEncoder().encodeToString(png()) else null,
            ),
        )

    /** 한 쪽에서 맨 아래 글자의 기준선. 위에서부터 잰다. */
    private fun lowestTextBaseline(pdf: ByteArray, page: Int): Float = Loader.loadPDF(pdf).use { document ->
        var lowest = 0f
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                textPositions.forEach { lowest = maxOf(lowest, it.yDirAdj) }
            }
        }
        stripper.startPage = page
        stripper.endPage = page
        stripper.getText(document)
        lowest
    }

    private fun assertA4Pages(pdf: ByteArray, pages: Int = FORM_COUNT) = Loader.loadPDF(pdf).use { document ->
        assertEquals(pages, document.numberOfPages)
        repeat(document.numberOfPages) { index ->
            val page = document.getPage(index).mediaBox
            // A4 세로 = 595 x 842 pt (소수점은 반올림해 본다)
            assertEquals(595, page.width.roundToInt())
            assertEquals(842, page.height.roundToInt())
        }
    }

    /** 졸업예정자라 3학년 2학기 열은 비고 나머지 세 열이 찬다. */
    private fun form() = ApplicationForm(
        applicantId = 12,
        userId = 10,
        name = "홍길동",
        phoneNumber = "010-1234-5678",
        birthdate = "2010-03-02",
        gender = ApplicationForm.Gender.MALE,
        address = "(34503) 대전광역시 유성구 가정북로 76 101동 1001호",
        photoFileId = "photo_a",
        region = Applicant.Region.DAEJEON,
        admissionType = Applicant.AdmissionType.MEISTER,
        specialNote = "국가유공자 자녀",
        graduationType = ApplicationForm.GraduationType.PROSPECTIVE,
        graduationDate = "2027-02",
        guardianName = "홍판서",
        guardianRelation = "부",
        guardianPhoneNumber = "010-9876-5432",
        school = ApplicationForm.MiddleSchool(
            name = "대덕중학교",
            studentNumber = "30115",
            phone = "042-000-0000",
            teacherName = "김선생",
        ),
        semesterGrades = listOf(null, grades("A"), grades("B"), grades("C")),
        academicRecord = ApplicationForm.AcademicRecord(
            absentCount = 1,
            lateCount = 2,
            earlyLeaveCount = 0,
            classAbsenceCount = 3,
            volunteerTime = 30,
            dsmAlgorithmAwarded = true,
            programmingCertified = true,
        ),
        introduction = "저는 어려서부터 컴퓨터로 무언가 만드는 일을 좋아했습니다.\n중학교에서는 정보 동아리 부장을 맡았습니다.",
        studyPlan = "입학 후에는 알고리즘과 웹 개발을 깊게 공부하고 싶습니다.\n3학년에는 팀 프로젝트로 서비스를 배포해 보겠습니다.",
    )

    private fun grades(grade: String) = ApplicationForm.SemesterGrades(
        korean = grade, society = grade, history = grade, math = grade,
        science = grade, technology = grade, english = grade,
    )

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()
}
