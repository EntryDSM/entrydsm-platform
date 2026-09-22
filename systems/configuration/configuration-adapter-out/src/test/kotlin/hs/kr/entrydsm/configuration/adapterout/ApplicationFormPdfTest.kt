package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * 요강이 "인터넷접수 후 출력"이라고 적은 서식 6개를 서식 원본 위에 찍는다. 지원자가 내려받아 그대로 인쇄해 우편으로
 * 보내는 종이라 서식마다 A4 한 장이고, 값은 원본의 칸 안에만 찍혀야 한다.
 *
 * 원서 값은 나눔고딕으로 찍고 서식 원본 글자는 다른 글꼴이다. 글꼴로 원서 값만 골라 위치를 잰다.
 */
class ApplicationFormPdfTest {

    /** 요강의 인터넷접수 후 출력 서식 수 — 입학원서·개인정보 동의·자기소개서·추천서·금연 동의·흡연검사 동의 */
    private val FORM_COUNT = 6

    private val adapter = ApplicationFormPdfAdapter()

    @Test
    fun `특별전형 원서는 서식 원본 여섯 장에 값과 사진을 찍는다`() {
        val pdf = adapter.render(form(), png())

        assertA4Pages(pdf, FORM_COUNT)
        assertEquals(1, images(pdf, page = 1))

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/ 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "application-form.pdf").writeBytes(pdf) }
    }

    @Test
    fun `일반전형 원서는 학교장 추천서를 빼고 다섯 장을 찍는다`() {
        val pdf = adapter.render(form().copy(admissionType = Applicant.AdmissionType.REGULAR), photo = null)

        assertA4Pages(pdf, FORM_COUNT - 1)
        val fourth = pageText(pdf, 4)
        assertTrue(fourth, fourth.contains("금연 동의서"))
    }

    @Test
    fun `작성 중이라 전형을 고르지 않은 원서도 빈 칸인 채로 다섯 장을 찍는다`() {
        val pdf = adapter.render(
            ApplicationForm(
                applicantId = 12, userId = 10, name = "홍길동", phoneNumber = null, birthdate = null,
                gender = null, address = null, photoFileId = null, region = null, admissionType = null,
                specialNote = null, graduationType = null, graduationDate = null, guardianName = null,
                guardianRelation = null, guardianPhoneNumber = null, school = null,
                semesterGrades = emptyList(), academicRecord = null, introduction = null, studyPlan = null,
            ),
            photo = null,
        )

        assertA4Pages(pdf, FORM_COUNT - 1)
    }

    @Test
    fun `접수번호는 네 자리로 채워 서식마다 같게 찍는다`() {
        val pdf = adapter.render(form(), photo = null)

        // 서식 2 는 원서에서 옮겨 적는 칸이 없다.
        listOf(1, 3, 4, 5, 6).forEach { page -> assertTrue("서식 $page", pageText(pdf, page).contains("0012")) }
    }

    @Test
    fun `학교코드와 출신지역은 출신 중학교 값으로 찍는다`() {
        val text = pageText(adapter.render(form(), photo = null), page = 1)

        assertTrue(text, text.contains("9299009"))
        // 가장 긴 출신지역이다. 한 칸에 한 줄로 찍혀야 두 토큰이 붙어 나온다.
        assertTrue(text, text.contains("제주특별자치도 서귀포시"))
    }

    @Test
    fun `긴 주소는 줄을 바꾸고 글자를 줄여 주소 칸 안에 다 찍는다`() {
        val address = "(34503) 대전광역시 유성구 가정북로 76번길 123-45 대덕소프트웨어마이스터고등학교 기숙사 제3생활관 502호"
        val pdf = adapter.render(form().copy(address = address), photo = null)
        val letters = address.count { !it.isWhitespace() }

        // 서식 1 주소 칸과, 인적사항 표 중 주소 칸이 가장 낮은 서식 6
        assertInside(stamped(pdf, page = 1), letters, left = 160.68f, top = 225.60f, right = 439.80f, bottom = 255.36f)
        assertInside(stamped(pdf, page = 6), letters, left = 182.04f, top = 177.48f, right = 535.68f, bottom = 198.00f)
    }

    @Test
    fun `금연 동의서 다짐 문장 괄호 사이에 이름을 찍고 20자 이름도 글자를 줄여 다 넣는다`() {
        // 괄호 사이는 이름을 찍는 칸 중 가장 좁다. 이름은 20자까지 저장된다(application applicants.name).
        listOf("홍길동", "가".repeat(20)).forEach { name ->
            val pdf = adapter.render(form().copy(name = name), photo = null)

            assertInside(
                stamped(pdf, page = 5), name.length,
                left = 112.24f, top = 330.65f, right = 202.83f, bottom = 352.65f,
            )
        }
    }

    @Test
    fun `빈칸 포함 1,600자 자기소개서도 본문 칸을 넘치지 않고 다 찍는다`() {
        val sentence = "저는 어려서부터 컴퓨터로 무언가 만드는 일을 좋아했고 중학교에서는 정보 동아리 부장을 맡았습니다. "
        val introduction = sentence.repeat(30).take(1596).chunked(320).joinToString("\n")
        val pdf = adapter.render(form().copy(introduction = introduction), photo = null)

        assertEquals(1600, introduction.length)
        assertInside(
            stamped(pdf, page = 3), introduction.count { !it.isWhitespace() },
            left = 59.52f, top = 278.16f, right = 535.80f, bottom = 491.88f,
        )
    }

    @Test
    fun `읽지 못하는 사진 형식이면 사진 칸을 비우고 원서는 찍는다`() {
        val webp = "RIFF0000WEBPVP8 ".toByteArray()
        val pdf = adapter.render(form(), webp)

        assertA4Pages(pdf, FORM_COUNT)
        assertEquals(0, images(pdf, page = 1))
    }

    @Test
    fun `글꼴에 없는 글자는 물음표로 바꿔 찍는다`() {
        val pdf = adapter.render(form().copy(introduction = "코딩이 좋아요 😀"), photo = null)

        assertTrue(pageText(pdf, 3).contains("코딩이 좋아요 ?"))
    }

    /** 칸 안에 기준선이 있는 원서 글자가 [count] 개이고, 모두 칸 좌우 안에 있다. 칸 아래로 넘친 글자는 수에서 빠진다. */
    private fun assertInside(glyphs: List<TextPosition>, count: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val inCell = glyphs.filter { it.yDirAdj in top..bottom }
        assertEquals(count, inCell.size)
        inCell.forEach {
            assertTrue("${it.unicode} at ${it.xDirAdj}", it.xDirAdj >= left && it.xDirAdj + it.widthDirAdj <= right)
        }
    }

    private fun pageText(pdf: ByteArray, page: Int): String = Loader.loadPDF(pdf).use { document ->
        PDFTextStripper().apply {
            startPage = page
            endPage = page
        }.getText(document)
    }

    /** 한 쪽에서 원서 값으로 찍힌 글자. 서식 원본 글자는 다른 글꼴이라 빠진다. */
    private fun stamped(pdf: ByteArray, page: Int): List<TextPosition> = Loader.loadPDF(pdf).use { document ->
        val glyphs = mutableListOf<TextPosition>()
        object : PDFTextStripper() {
            override fun writeString(text: String, textPositions: List<TextPosition>) {
                glyphs += textPositions.filter { "NanumGothic" in it.font.name && it.unicode.isNotBlank() }
            }
        }.apply {
            startPage = page
            endPage = page
        }.getText(document)
        glyphs
    }

    private fun images(pdf: ByteArray, page: Int): Int = Loader.loadPDF(pdf).use { document ->
        val resources = document.getPage(page - 1).resources
        resources.xObjectNames.count { resources.isImageXObject(it) }
    }

    private fun assertA4Pages(pdf: ByteArray, pages: Int) = Loader.loadPDF(pdf).use { document ->
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
        // 기관코드 표에서 출신지역이 가장 길게 찍히는 곳(제주특별자치도 서귀포시)의 학교다.
        school = ApplicationForm.MiddleSchool(
            name = "서귀포중학교",
            studentNumber = "30115",
            phone = "064-730-7900",
            teacherName = "김선생",
            code = "9299009",
            address = "제주특별자치도 서귀포시 태평로 474",
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
        val image = BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(0x8899AA)
            fillRect(0, 0, 300, 400)
            dispose()
        }
        ImageIO.write(image, "png", it)
    }.toByteArray()
}
