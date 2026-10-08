package hs.kr.entrydsm.application.administration.adapterout.document

import hs.kr.entrydsm.configuration.domain.document.*

import hs.kr.entrydsm.application.administration.domain.document.Applicant
import hs.kr.entrydsm.application.administration.domain.document.ApplicationForm
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** 체크 선을 픽셀로 볼 때 쓰는 렌더링 배율 */
    private val SCALE = 4f

    @Test
    fun `특별전형 원서는 서식 원본 여섯 장에 값과 사진을 찍는다`() {
        val pdf = adapter.render(form(), png())

        assertA4Pages(pdf, FORM_COUNT)
        assertEquals(1, images(pdf, page = 1))

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/ 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "application-form.pdf").writeBytes(pdf) }
    }

    @Test
    fun `검정고시 원서는 추천서 없는 검정고시 서식 다섯 장에 찍고 점수 표에 검정고시 점수를 찍는다`() {
        val ged = form().copy(
            graduationType = ApplicationForm.GraduationType.GED,
            graduationDate = null,
            school = null,
            semesterGrades = listOf(null, null, null, null),
            gedScores = ApplicationForm.SemesterGrades("95", "88", "100", "76", "90", "85", "99"),
            // 검정고시 점수를 저장하면 출결이 0 인 성적 기록이 같이 생긴다.
            academicRecord = ApplicationForm.AcademicRecord(0, 0, 0, 0, 0, dsmAlgorithmAwarded = true, programmingCertified = false),
        )
        val pdf = adapter.render(ged, png())
        val glyphs = stamped(pdf, page = 1)

        // 칸 안에 기준선과 좌우가 다 드는 원서 글자를 위 행부터 읽는다.
        fun cell(left: Float, top: Float, right: Float, bottom: Float) = glyphs
            .filter { it.yDirAdj in top..bottom && it.xDirAdj >= left && it.xDirAdj + it.widthDirAdj <= right }
            .sortedBy { it.yDirAdj }
            .joinToString("") { it.unicode }

        assertA4Pages(pdf, FORM_COUNT - 1)
        assertTrue(pageText(pdf, 1).contains("검정고시 점수"))
        assertEquals(1, images(pdf, page = 1))
        // 국어 95 · 사회 88 · 역사 100 · 수학 76 · 과학 90 · 기술·가정 85 · 영어 99
        assertEquals("958810076908599", cell(160.68f, 340.68f, 399.24f, 470.04f))
        // 졸업구분 칸에는 "검정고시" 가 인쇄돼 있고, 점수 표 오른쪽 빈 칸에는 출결을 찍지 않는다.
        assertEquals("", cell(160.68f, 201.48f, 439.80f, 225.60f))
        assertEquals("", cell(399.24f, 303.72f, 536.40f, 414.60f))
        assertEquals("O", cell(477.72f, 433.08f, 536.40f, 451.56f))
        assertEquals("-", cell(477.72f, 451.56f, 536.40f, 470.04f))
        // 추천서가 빠져 서식 5·6 이 4·5쪽이다. 특별전형이어도 같다.
        listOf(3, 4, 5).forEach { page -> assertTrue("${page}쪽", pageText(pdf, page).contains("0012")) }
        assertTrue(pageText(pdf, 4).contains("금연 동의서"))
        assertFalse(stamped(pdf, page = 4).any { it.unicode == "○" })
        assertInside(stamped(pdf, page = 4), 3, left = 112.24f, top = 330.65f, right = 202.83f, bottom = 352.65f)
        // 일반전형이어도 추천서를 빼느라 금연 동의서를 지우지 않는다.
        assertA4Pages(adapter.render(ged.copy(admissionType = Applicant.AdmissionType.REGULAR), photo = null), FORM_COUNT - 1)

        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "application-form-ged.pdf").writeBytes(pdf) }
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
    fun `특기사항·가산점·교과성적의 빈칸에는 하이픈을 찍는다`() {
        val form = form().copy(
            specialNote = null,
            // 3학년 2학기 열은 졸업예정이라 통째로 없고, 직전학기 역사는 미이수다.
            semesterGrades = listOf(null, grades("A"), grades("B").copy(history = ""), grades("C")),
            academicRecord = form().academicRecord!!.copy(programmingCertified = false),
        )
        val glyphs = stamped(adapter.render(form, photo = null), page = 1)
        fun cell(left: Float, top: Float, right: Float, bottom: Float) = glyphs
            .filter { it.yDirAdj in top..bottom && it.xDirAdj >= left && it.xDirAdj + it.widthDirAdj <= right }
            .sortedBy { it.yDirAdj }
            .joinToString("") { it.unicode }

        assertEquals("-", cell(399.24f, 279.48f, 536.40f, 303.72f))
        assertEquals("-------", cell(109.32f, 340.68f, 181.80f, 470.04f))
        assertEquals("BB-BBBB", cell(254.28f, 340.68f, 326.76f, 470.04f))
        assertEquals("O", cell(477.72f, 433.08f, 536.40f, 451.56f))
        assertEquals("-", cell(477.72f, 451.56f, 536.40f, 470.04f))
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
    fun `추천서 괄호에 중학교를 뗀 출신 중학교 이름과 반을 찍고 가장 긴 학교 이름도 괄호 안에 다 넣는다`() {
        // 괄호 뒤에 "중학교" 가 인쇄돼 있다. 긴 쪽은 기관코드 표에서 "중학교" 로 끝나는 이름 중 가장 긴 학교다.
        listOf("서귀포중학교" to "서귀포", "대구가톨릭대학교사범대학부속무학중학교" to "대구가톨릭대학교사범대학부속무학")
            .forEach { (name, printed) ->
                val pdf = adapter.render(form().let { it.copy(school = it.school!!.copy(name = name)) }, photo = null)
                fun cell(page: Int, left: Float, top: Float, right: Float, bottom: Float) = stamped(pdf, page)
                    .filter { it.yDirAdj in top..bottom && it.xDirAdj >= left && it.xDirAdj + it.widthDirAdj <= right }
                    .joinToString("") { it.unicode }

                assertEquals(printed, cell(1, 254.70f, 690.18f, 362.59f, 712.18f))
                assertEquals(printed, cell(4, 302.90f, 210.40f, 384.39f, 234.40f))
                assertEquals("1", cell(4, 345.06f, 240.40f, 374.20f, 264.40f))
                assertEquals(printed, cell(4, 171.98f, 637.83f, 299.12f, 665.83f))
            }
    }

    @Test
    fun `출신 중학교가 없거나 학번에서 반을 뽑지 못한 원서는 추천서 학교·반 괄호를 비운다`() {
        // 검정고시 지원자는 출신 중학교가 없다. 학번이 다섯 자리가 아니면 application 이 반을 보내지 않는다.
        val noSchool = adapter.render(form().copy(school = null), photo = null)
        val noClass = adapter.render(form().let { it.copy(school = it.school!!.copy(classNumber = null)) }, photo = null)
        fun cell(pdf: ByteArray, page: Int, left: Float, top: Float, right: Float, bottom: Float) = stamped(pdf, page)
            .filter { it.yDirAdj in top..bottom && it.xDirAdj >= left && it.xDirAdj + it.widthDirAdj <= right }
            .joinToString("") { it.unicode }

        assertEquals("", cell(noSchool, 1, 254.70f, 690.18f, 362.59f, 712.18f))
        assertEquals("", cell(noSchool, 4, 302.90f, 210.40f, 384.39f, 234.40f))
        assertEquals("", cell(noSchool, 4, 345.06f, 240.40f, 374.20f, 264.40f))
        assertEquals("", cell(noSchool, 4, 171.98f, 637.83f, 299.12f, 665.83f))
        assertEquals("", cell(noClass, 4, 345.06f, 240.40f, 374.20f, 264.40f))
        assertEquals("서귀포", cell(noClass, 4, 302.90f, 210.40f, 384.39f, 234.40f))
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

    @Test
    fun `자기소개서와 학업계획서를 서식 3 한 장씩 분리한다`() {
        val introduction = adapter.renderEssay(form(), introduction = true)
        val studyPlan = adapter.renderEssay(form(), introduction = false)

        assertA4Pages(introduction, 1)
        assertTrue(pageText(introduction, 1).contains("저는 어려서부터"))
        assertTrue(!pageText(introduction, 1).contains("입학 후에는"))
        assertA4Pages(studyPlan, 1)
        assertTrue(pageText(studyPlan, 1).contains("입학 후에는"))
        assertTrue(!pageText(studyPlan, 1).contains("저는 어려서부터"))
    }

    /** 칸 안에 기준선이 있는 원서 글자가 [count] 개이고, 모두 칸 좌우 안에 있다. 칸 아래로 넘친 글자는 수에서 빠진다. */
    @Test
    fun `등록 서류는 원본 첫 장 입학 동의서 칸에 지원자 정보를 찍고 나머지 장은 그대로 둔다`() {
        val pdf = adapter.renderRegistrationDocument(form().copy(examineeNumber = "11001"), registrationTemplate())

        assertA4Pages(pdf, 3)
        val glyphs = stamped(pdf, page = 1)
        fun cell(left: Float, top: Float, right: Float, bottom: Float) = glyphs
            .filter { it.yDirAdj in top..bottom && it.xDirAdj >= left && it.xDirAdj + it.widthDirAdj <= right }
            .joinToString("") { it.unicode }

        assertEquals("11001", cell(407.16f, 123.60f, 538.68f, 144.72f))
        assertEquals("홍길동", cell(157.92f, 144.72f, 318.84f, 170.64f))
        assertEquals("010-1234-5678", cell(157.92f, 170.64f, 318.84f, 196.44f))
        assertEquals("2010", cell(407.16f, 170.64f, 464.88f, 196.44f))
        assertEquals("3", cell(475.92f, 170.64f, 492.37f, 196.44f))
        assertEquals("2", cell(503.41f, 170.64f, 519.86f, 196.44f))
        assertEquals("홍판서", cell(157.92f, 222.36f, 318.84f, 248.88f))
        assertEquals("부", cell(475.75f, 222.36f, 519.47f, 248.88f))
        assertEquals("010-9876-5432", cell(157.92f, 248.88f, 538.68f, 274.80f))
        assertTrue(cell(157.92f, 196.44f, 538.68f, 222.36f).startsWith("(34503)대전광역시"))
        // 2·3쪽은 원본 글자만 그대로 남는다.
        assertEquals(0, stamped(pdf, page = 2).size + stamped(pdf, page = 3).size)
        assertTrue(pageText(pdf, 2).contains("PAGE-2"))
        assertTrue(pageText(pdf, 3).contains("PAGE-3"))

        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "registration-document.pdf").writeBytes(pdf) }
    }

    @Test
    fun `성별은 인쇄된 남·여 네모 중 해당하는 쪽에만 체크를 긋는다`() {
        fun marks(gender: ApplicationForm.Gender?): Pair<Boolean, Boolean> {
            val pdf = adapter.renderRegistrationDocument(form().copy(gender = gender), registrationTemplate())
            // 원본이 빈 A4 라 네모 자리의 어두운 픽셀은 체크 선뿐이다.
            val image = Loader.loadPDF(pdf).use { PDFRenderer(it).renderImageWithDPI(0, 72f * SCALE) }
            fun inked(left: Float, right: Float) = (left.px()..right.px()).any { x ->
                (152.04f.px()..163.08f.px()).any { y -> Color(image.getRGB(x, y)).red < 128 }
            }
            return inked(443.88f, 453.69f) to inked(481.09f, 490.90f)
        }

        assertEquals(true to false, marks(ApplicationForm.Gender.MALE))
        assertEquals(false to true, marks(ApplicationForm.Gender.FEMALE))
        assertEquals(false to false, marks(null))
    }

    private fun Float.px() = (this * SCALE).roundToInt()

    @Test
    fun `등록 서류의 긴 주소는 줄을 바꿔 주소 칸 안에 다 찍는다`() {
        // 주소는 기본·상세 255자씩 저장된다(application applicants.address_base·address_detail). 한 줄로는 4pt 로도 넘친다.
        val address = "(34503) 대전광역시 유성구 가정북로 76번길 123-45 대덕소프트웨어마이스터고등학교 기숙사 제3생활관 502호 " +
            "대전광역시 유성구 가정북로 76번길 123-45 대덕소프트웨어마이스터고등학교 기숙사 제3생활관 앞 경비실 옆 우편함"
        val pdf = adapter.renderRegistrationDocument(form().copy(address = address), registrationTemplate())

        assertInside(
            stamped(pdf, page = 1), address.count { !it.isWhitespace() },
            left = 157.92f, top = 196.44f, right = 538.68f, bottom = 222.36f,
        )
    }

    @Test
    fun `PDF 로 열리고 첫 장이 있어야 등록 서류 원본으로 받는다`() {
        val empty = PDDocument().use { document -> ByteArrayOutputStream().also { document.save(it) }.toByteArray() }

        assertTrue(adapter.isRegistrationTemplate(registrationTemplate()))
        assertFalse(adapter.isRegistrationTemplate(empty))
        assertFalse(adapter.isRegistrationTemplate("PK\u0003\u0004 hwpx".toByteArray()))
    }

    /**
     * 등록 서류 원본은 저장소에 두지 않는다(관리자가 S3 에 올린다). 같은 크기의 A4 세 장으로 대신하고, 원본이 그대로
     * 남는지 보도록 각 장 아래쪽에 "PAGE-n" 을 찍어 둔다.
     */
    private fun registrationTemplate(): ByteArray = PDDocument().use { document ->
        repeat(3) { index ->
            val page = PDPage(PDRectangle.A4).also(document::addPage)
            PDPageContentStream(document, page).use {
                it.beginText()
                it.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                it.newLineAtOffset(60f, 40f)
                it.showText("PAGE-${index + 1}")
                it.endText()
            }
        }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

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
            classNumber = "1",
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
