package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm
import hs.kr.entrydsm.configuration.domain.document.ReceiptNumber
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicationFormPdfPort
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream

/** 학교가 한글에서 A4 로 뽑은 2027학년도 요강 서식 1~6. 한 서식이 한 장이다. */
private const val TEMPLATE_RESOURCE = "/forms/application-form.pdf"

/** 칸 테두리에서 글자까지 띄우는 간격(pt). 서식 1 교과성적 칸(18.48pt)에 10pt 한 줄이 들어가도록 위아래는 좁게 둔다. */
private const val PADDING_X = 4f
private const val PADDING_Y = 2f
private const val MIN_FONT_SIZE = 4f
private const val FONT_SIZE_STEP = 0.25f
private const val LINE_HEIGHT = 1.25f

/** 한글 글자 몸통의 가운데가 기준선에서 얼마나 위에 있는지(글자 크기 대비). 칸 가운데에 맞출 때 쓴다. */
private const val GLYPH_CENTER = 0.36f

/** 서식 3·5·6 인적사항 표의 글자 크기. 서식 1 표보다 라벨이 크게 인쇄돼 있다. */
private const val PERSONAL_INFO_FONT_SIZE = 11f

private const val CM = 72f / 2.54f

private val log = LoggerFactory.getLogger(ApplicationFormPdfAdapter::class.java)

/**
 * 요강 서식 원본 위에 원서 값을 찍는다.
 *
 * 서식을 HTML 로 다시 그리면 글꼴·칸 높이·줄 간격이 원본과 어긋난다. 그래서 서식 원본을 그대로 깔고 값만 원본에서 잰
 * 칸 좌표에 올린다. 좌표는 원본 PDF 에서 위·왼쪽을 기준으로 잰 pt 이고, 칸은 (왼쪽, 위, 오른쪽, 아래) 순서로 적는다.
 *
 * 해마다 요강이 바뀌면 [TEMPLATE_RESOURCE] 를 새 서식으로 갈고 칸 좌표를 다시 잰다. 학년도·날짜 골격도 서식에 인쇄돼 있다.
 *
 * 날짜·서명 칸과 `( )`·`[ ]` 안은 지원자·학교가 손으로 쓰는 칸이라 비운다.
 */
@Component
class ApplicationFormPdfAdapter : ApplicationFormPdfPort {

    private val template: ByteArray by lazy { resource(TEMPLATE_RESOURCE) }
    private val fontFile: ByteArray by lazy { resource(FONT_RESOURCE) }

    override fun render(form: ApplicationForm, photo: ByteArray?): ByteArray =
        Loader.loadPDF(template).use { document ->
            val font = PDType0Font.load(document, fontFile.inputStream())
            val receipt = ReceiptNumber.of(form.applicantId)
            val pages = document.pages.toList()

            Sheet(document, pages[0], font).use { it.application(form, receipt, photo) }
            Sheet(document, pages[2], font).use { it.essays(form, receipt) }
            Sheet(document, pages[4], font).use {
                it.personalInfo(form, receipt, rows = floatArrayOf(136.44f, 162.60f, 188.76f, 215.04f))
            }
            Sheet(document, pages[5], font).use {
                it.personalInfo(form, receipt, rows = floatArrayOf(136.44f, 156.96f, 177.48f, 198.00f))
            }
            // 서식 4 는 특별전형 지원자만 내는 추천서다. 일반전형이거나 아직 전형을 고르지 않았으면 장을 통째로 뺀다.
            when (form.admissionType) {
                Applicant.AdmissionType.MEISTER, Applicant.AdmissionType.SOCIAL ->
                    Sheet(document, pages[3], font).use { it.recommendation(form, receipt) }
                else -> document.removePage(pages[3])
            }

            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Resource not found: $path" }.use { it.readBytes() }
}

/**
 * 서식 1 입학원서. 수험번호 칸은 서식에 "*기재하지 않음" 이 인쇄돼 있다.
 *
 * 학교코드·출신지역·출신학교는 출신 중학교 값이라 검정고시 지원자는 빈다. 출신지역은 중학교 소재지다
 * ([ApplicationForm.MiddleSchool.originRegion]).
 *
 * ponytail: 보훈번호는 원서에 저장하는 값이 없어 비운다. 수집하기로 하면 [ApplicationForm] 에 담아 찍는다.
 */
private fun Sheet.application(form: ApplicationForm, receipt: String, photo: ByteArray?) {
    val school = form.school
    text(109.32f, 104.88f, 219.36f, 129.00f, receipt)
    text(274.56f, 104.88f, 382.20f, 129.00f, school?.code)

    text(160.68f, 129.00f, 274.56f, 153.12f, form.name)
    text(326.64f, 129.00f, 439.80f, 153.12f, form.phoneNumber)
    text(160.68f, 153.12f, 274.56f, 177.36f, form.birthdate)
    text(326.64f, 153.12f, 439.80f, 177.36f, school?.originRegion)
    text(160.68f, 177.36f, 274.56f, 201.48f, form.gender?.label)
    text(326.64f, 177.36f, 439.80f, 201.48f, school?.name)
    text(160.68f, 201.48f, 439.80f, 225.60f, graduation(form))
    text(160.68f, 225.60f, 439.80f, 255.36f, form.address, align = Align.LEFT, wrap = true)
    // 서식이 적어 둔 3cm×4cm 크기로 사진 칸 가운데에 넣는다.
    photo?.let {
        val (x, y) = (439.80f + 536.40f) / 2 to (129.00f + 255.36f) / 2
        image(x - 1.5f * CM, y - 2 * CM, x + 1.5f * CM, y + 2 * CM, it)
    }

    text(160.68f, 255.36f, 254.28f, 279.48f, form.guardianName)
    text(305.04f, 255.36f, 382.20f, 279.48f, form.guardianRelation)
    text(439.80f, 255.36f, 536.40f, 279.48f, form.guardianPhoneNumber)

    text(109.32f, 279.48f, 160.68f, 303.72f, form.region?.label)
    text(213.72f, 279.48f, 346.44f, 303.72f, form.admissionType?.label)
    text(399.24f, 279.48f, 536.40f, 303.72f, form.specialNote)

    // 교과성적 표. 행은 국어~영어, 열은 3학년 2학기·3학년 1학기·직전학기·직전전학기다. 반영할 성적이 없는 열은 빈다.
    val rows = floatArrayOf(340.68f, 359.16f, 377.64f, 396.12f, 414.60f, 433.08f, 451.56f, 470.04f)
    val columns = floatArrayOf(109.32f, 181.80f, 254.28f, 326.76f, 399.24f)
    form.semesterGrades.take(ApplicationForm.SEMESTER_COLUMN_COUNT).forEachIndexed { column, grades ->
        grades?.inFormOrder()?.forEachIndexed { row, grade ->
            text(columns[column], rows[row], columns[column + 1], rows[row + 1], grade)
        }
    }

    // 출결 칸은 단위(일·회·시간)가 오른쪽에 인쇄돼 있어 그 왼쪽에 숫자만 찍는다.
    form.academicRecord?.let { record ->
        text(477.72f, 322.20f, 519.00f, 340.68f, record.absentCount.toString())
        text(477.72f, 340.68f, 519.00f, 359.16f, record.lateCount.toString())
        text(477.72f, 359.16f, 519.00f, 377.64f, record.earlyLeaveCount.toString())
        text(477.72f, 377.64f, 519.00f, 396.12f, record.classAbsenceCount.toString())
        text(477.72f, 396.12f, 509.00f, 414.60f, record.volunteerTime.toString())
        text(477.72f, 433.08f, 536.40f, 451.56f, if (record.dsmAlgorithmAwarded) "O" else null)
        text(477.72f, 451.56f, 536.40f, 470.04f, if (record.programmingCertified) "O" else null)
    }

    // 원서작성자 칸에는 "교사:" 와 "(서명 또는 인)" 이 인쇄돼 있어 그 사이에 담임 이름만 찍는다.
    text(166.57f, 751.44f, 241.56f, 775.56f, school?.teacherName)
    text(399.24f, 751.44f, 536.40f, 775.56f, school?.phone)
}

/** 서식 3 자기소개서·학업계획서. 본문은 지원자가 쓴 줄바꿈을 살려 칸 위부터 채운다. */
private fun Sheet.essays(form: ApplicationForm, receipt: String) {
    // 성명 칸 오른쪽에는 "(서명)" 이 인쇄돼 있어 그 앞까지만 쓴다.
    personalInfo(form, receipt, rows = floatArrayOf(139.20f, 165.48f, 191.64f, 217.80f), nameRight = 278.00f)
    text(59.52f, 278.16f, 535.80f, 491.88f, form.introduction, align = Align.LEFT, wrap = true, fromTop = true)
    text(59.52f, 536.16f, 535.80f, 749.88f, form.studyPlan, align = Align.LEFT, wrap = true, fromTop = true)
}

/**
 * 서식 3·5·6 의 인적사항 표. 세 서식 모두 성명·접수번호 / 전화번호·출신학교 / 주소 세 줄이고 줄 높이만 다르다.
 *
 * @param rows 표의 가로선 네 개(위→아래)
 */
private fun Sheet.personalInfo(form: ApplicationForm, receipt: String, rows: FloatArray, nameRight: Float = 316.92f) {
    text(182.04f, rows[0], nameRight, rows[1], form.name, PERSONAL_INFO_FONT_SIZE)
    text(400.80f, rows[0], 535.68f, rows[1], receipt, PERSONAL_INFO_FONT_SIZE)
    text(182.04f, rows[1], 316.92f, rows[2], form.phoneNumber, PERSONAL_INFO_FONT_SIZE)
    text(400.80f, rows[1], 535.68f, rows[2], form.school?.name, PERSONAL_INFO_FONT_SIZE)
    text(182.04f, rows[2], 535.68f, rows[3], form.address, PERSONAL_INFO_FONT_SIZE, Align.LEFT, wrap = true)
}

/**
 * 서식 4 학교장 추천서. 학교·반·날짜·담임 이름은 학교가 손으로 쓰는 칸이라 비운다.
 * 접수번호와 성명은 인쇄된 "접수번호:"·"성 명 :" 뒤에 같은 크기로 이어 쓰고, 추천분야 표에는 지원한 전형 칸에만 ○ 를 찍는다.
 */
private fun Sheet.recommendation(form: ApplicationForm, receipt: String) {
    text(387.00f, 148.74f, 533.04f, 168.74f, receipt, size = 12f, align = Align.LEFT)
    text(344.00f, 270.40f, 533.04f, 294.40f, form.name, size = 15f, align = Align.LEFT)
    text(93.36f, 398.64f, 295.08f, 426.24f, "○".takeIf { form.admissionType == Applicant.AdmissionType.MEISTER }, size = 14f)
    text(295.08f, 398.64f, 496.80f, 426.24f, "○".takeIf { form.admissionType == Applicant.AdmissionType.SOCIAL }, size = 14f)
}

/** 졸업구분 칸. "졸업예정 (2027-02)" 처럼 구분 뒤에 졸업 연월을 붙인다. */
private fun graduation(form: ApplicationForm): String? {
    val type = form.graduationType?.label ?: return form.graduationDate
    return form.graduationDate?.let { "$type ($it)" } ?: type
}

private enum class Align { LEFT, CENTER }

/** 서식 한 장에 값을 덧찍는다. */
private class Sheet(private val document: PDDocument, page: PDPage, private val font: PDFont) : AutoCloseable {

    private val pageHeight = page.mediaBox.height

    // resetContext: 원본 내용이 좌표계를 바꿔 둔 채 끝나도 기본 좌표계에서 찍는다.
    private val stream = PDPageContentStream(document, page, AppendMode.APPEND, true, true)

    /**
     * 칸 안에 값을 찍는다. 칸을 넘치면 글자를 [MIN_FONT_SIZE] 까지 줄인다. [wrap] 칸은 칸 폭에서 줄을 바꾸고 줄 수가
     * 칸 높이를 넘칠 때 줄이며, 그래도 넘치는 줄은 버린다. 줄 묶음은 칸 가운데에 두고, [fromTop] 이면 위부터 채운다.
     */
    fun text(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        value: String?,
        size: Float = 10f,
        align: Align = Align.CENTER,
        wrap: Boolean = false,
        fromTop: Boolean = false,
    ) {
        val text = value?.takeIf { it.isNotBlank() }?.let { printable(it) } ?: return
        val width = right - left - 2 * PADDING_X
        // 위부터 채우는 본문은 왼쪽과 같은 여백을 둔다.
        val topPadding = if (fromTop) PADDING_X else PADDING_Y
        val height = bottom - top - topPadding - PADDING_Y
        fun layout(fontSize: Float) = if (wrap) wrap(text, fontSize, width) else listOf(text.replace('\n', ' '))

        var fontSize = size
        var lines = layout(fontSize)
        while (fontSize > MIN_FONT_SIZE &&
            (lines.any { measure(it, fontSize) > width } || lines.size * fontSize * LINE_HEIGHT > height)
        ) {
            fontSize -= FONT_SIZE_STEP
            lines = layout(fontSize)
        }

        val leading = fontSize * LINE_HEIGHT
        lines = lines.take(maxOf(1, (height / leading).toInt()))
        val blockTop = if (fromTop) top + topPadding else (top + bottom - lines.size * leading) / 2
        stream.beginText()
        stream.setFont(font, fontSize)
        lines.forEachIndexed { index, line ->
            val x = when (align) {
                Align.LEFT -> left + PADDING_X
                Align.CENTER -> (left + right - measure(line, fontSize)) / 2
            }
            val baseline = blockTop + leading * (index + 0.5f) + fontSize * GLYPH_CENTER
            stream.setTextMatrix(Matrix.getTranslateInstance(x, pageHeight - baseline))
            stream.showText(line)
        }
        stream.endText()
    }

    /**
     * 사진을 칸 안에 비율을 지켜 가장 크게 넣는다. 사진 뒤를 흰색으로 먼저 칠해 서식에 인쇄된 "사진(3cm×4cm)" 이
     * 투명한 배경으로 비치지 않게 한다. 읽지 못하는 형식(webp 등)이면 칸을 비운다.
     */
    fun image(left: Float, top: Float, right: Float, bottom: Float, bytes: ByteArray) {
        val image = runCatching { PDImageXObject.createFromByteArray(document, bytes, "photo") }
            .onFailure { log.warn("Photo left out of the application form: {}", it.message) }
            .getOrNull() ?: return
        val scale = minOf((right - left) / image.width, (bottom - top) / image.height)
        val (width, height) = image.width * scale to image.height * scale
        val (x, y) = (left + right - width) / 2 to pageHeight - (top + bottom + height) / 2
        stream.saveGraphicsState()
        stream.setNonStrokingColor(Color.WHITE)
        stream.addRect(x, y, width, height)
        stream.fill()
        stream.drawImage(image, x, y, width, height)
        stream.restoreGraphicsState()
    }

    override fun close() = stream.close()

    /** 글꼴에 없는 글자(이모지 등)는 PDFBox 가 찍지 못하고 예외를 던지므로 물음표로 바꾼다. 탭은 띄어쓰기로 본다. */
    private fun printable(value: String): String = buildString {
        value.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ').codePoints().forEach { codePoint ->
            val char = String(Character.toChars(codePoint))
            append(if (char == "\n" || runCatching { font.encode(char) }.isSuccess) char else "?")
        }
    }

    /** 띄어쓰기에서 줄을 바꾸고, 한 줄보다 긴 낱말은 글자 단위로 끊는다. 지원자가 넣은 줄바꿈은 그대로 둔다. */
    private fun wrap(text: String, fontSize: Float, width: Float): List<String> = text.split('\n').flatMap { paragraph ->
        val lines = mutableListOf<String>()
        var line = ""
        paragraph.split(Regex("(?<= )")).forEach { word ->
            if (measure((line + word).trimEnd(), fontSize) <= width) {
                line += word
                return@forEach
            }
            if (line.isNotBlank()) lines += line.trimEnd()
            line = ""
            word.forEach { char ->
                if (line.isNotEmpty() && measure(line + char, fontSize) > width) {
                    lines += line
                    line = ""
                }
                line += char
            }
        }
        lines + line.trimEnd()
    }

    private fun measure(text: String, fontSize: Float) = font.getStringWidth(text) / 1000 * fontSize
}
