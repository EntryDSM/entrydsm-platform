package hs.kr.entrydsm.application.administration.adapterout.document

import hs.kr.entrydsm.application.administration.domain.document.Applicant
import hs.kr.entrydsm.application.administration.domain.document.ApplicationForm
import hs.kr.entrydsm.application.administration.domain.document.ReceiptNumber
import hs.kr.entrydsm.application.administration.domain.document.port.out.ApplicationFormPdfPort
import org.apache.pdfbox.Loader
import org.apache.pdfbox.contentstream.operator.Operator
import org.apache.pdfbox.contentstream.operator.OperatorName
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSNumber
import org.apache.pdfbox.pdfparser.PDFStreamParser
import org.apache.pdfbox.pdfwriter.ContentStreamWriter
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.PDStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.util.Matrix
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream

/** 학교가 한글에서 A4 로 뽑은 2027학년도 요강 서식 1~6. 한 서식이 한 장이다. */
private const val TEMPLATE_RESOURCE = "/forms/application-form.pdf"

/**
 * 검정고시 지원자용 서식. 서식 1 이 검정고시 점수 표로 바뀌고 서식 4 추천서가 없어 서식 1·2·3·5·6 다섯 장이다.
 * 서식 2·3·5·6 은 출신학교 칸에 "-" 가 인쇄된 것 말고는 [TEMPLATE_RESOURCE] 와 칸 위치가 같다.
 */
private const val GED_TEMPLATE_RESOURCE = "/forms/application-form-ged.pdf"

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

/** 서식 1 의 특기사항·가산점·교과성적 칸에 값이 없을 때 빈칸 대신 찍는 표시. 학교가 요청했다. */
private const val EMPTY_CELL = "-"

/** 서식 3 본문 칸의 좌우와, 자기소개서·학업계획서 본문 칸의 위아래. 학업계획서 칸 아래가 서식의 아래 여백이다. */
private const val ESSAY_LEFT = 59.52f
private const val ESSAY_RIGHT = 535.80f
private const val INTRODUCTION_TOP = 278.16f
private const val INTRODUCTION_BOTTOM = 491.88f
/** 학업계획서 안내 칸 윗선. 자기소개서 칸과의 사이에 틈이 있다. */
private const val STUDY_PLAN_HEAD_TOP = 499.68f
private const val STUDY_PLAN_TOP = 536.16f
private const val STUDY_PLAN_BOTTOM = 749.88f

/** 서식 3 본문 글자 크기. 글이 길어도 줄이지 않고 칸을 늘린다. */
private const val ESSAY_FONT_SIZE = 10f

/** 본문 칸 테두리 굵기. 서식 원본의 선과 같다. */
private const val BORDER_WIDTH = 0.36f

/** 서식 3 뒤에 이어 붙이는 장의 위 여백. 서식 3 머리글("<서식 3>")과 같은 높이다. */
private const val CONTINUED_TOP = 70.68f

private val log = LoggerFactory.getLogger(ApplicationFormPdfAdapter::class.java)

/**
 * 요강 서식 원본 위에 원서 값을 찍는다.
 *
 * 서식을 HTML 로 다시 그리면 글꼴·칸 높이·줄 간격이 원본과 어긋난다. 그래서 서식 원본을 그대로 깔고 값만 원본에서 잰
 * 칸 좌표에 올린다. 좌표는 원본 PDF 에서 위·왼쪽을 기준으로 잰 pt 이고, 칸은 (왼쪽, 위, 오른쪽, 아래) 순서로 적는다.
 *
 * 해마다 요강이 바뀌면 [TEMPLATE_RESOURCE] 를 새 서식으로 갈고 칸 좌표를 다시 잰다. 학년도·날짜 골격도 서식에 인쇄돼 있다.
 *
 * 날짜·서명 칸과 `( )`·`[ ]` 안은 지원자·학교가 손으로 쓰는 칸이라 비운다. 학교가 채워 달라고 표시한 괄호만 채운다 —
 * 서식 5 다짐 문장의 이름, 서식 1·4 의 출신 중학교 이름, 서식 4 의 반.
 */
@Component
class ApplicationFormPdfAdapter : ApplicationFormPdfPort {

    private val template: ByteArray by lazy { resource(TEMPLATE_RESOURCE) }
    private val gedTemplate: ByteArray by lazy { resource(GED_TEMPLATE_RESOURCE) }
    private val fontFile: ByteArray by lazy { resource(FONT_RESOURCE) }

    override fun render(form: ApplicationForm, photo: ByteArray?): ByteArray =
        Loader.loadPDF(templateOf(form)).use { document ->
            val font = PDType0Font.load(document, fontFile.inputStream())
            val receipt = ReceiptNumber.of(form.applicantId)
            val pages = document.pages.toList()
            // 검정고시 서식에는 서식 4 가 없어 서식 5·6 이 한 장씩 앞에 있다.
            val ged = form.ged
            val (noSmoking, smokingTest) = if (ged) pages[3] to pages[4] else pages[4] to pages[5]

            Sheet(document, pages[0], font).use { it.application(form, receipt, photo) }
            document.essays(pages[2], font, form, receipt)
            Sheet(document, noSmoking, font).use {
                it.personalInfo(form, receipt, rows = floatArrayOf(136.44f, 162.60f, 188.76f, 215.04f))
                // 다짐 문장 "나 (    )은(는)" 의 괄호 사이. 나눔고딕 12pt 가 인쇄된 본문 글자와 크기가 같다.
                // 칸 높이 검사로 글자가 줄지 않게 위아래는 본문 줄 가운데(341.65)에서 넉넉히 잡는다.
                it.text(112.24f, 330.65f, 202.83f, 352.65f, form.name, size = 12f)
            }
            Sheet(document, smokingTest, font).use {
                it.personalInfo(form, receipt, rows = floatArrayOf(136.44f, 156.96f, 177.48f, 198.00f))
            }
            // 서식 4 는 특별전형 지원자만 내는 추천서다. 일반전형이거나 아직 전형을 고르지 않았으면 장을 통째로 뺀다.
            if (!ged) {
                when (form.admissionType) {
                    Applicant.AdmissionType.MEISTER, Applicant.AdmissionType.SOCIAL ->
                        Sheet(document, pages[3], font).use { it.recommendation(form, receipt) }
                    else -> document.removePage(pages[3])
                }
            }

            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    override fun renderEssay(form: ApplicationForm, introduction: Boolean): ByteArray =
        Loader.loadPDF(templateOf(form)).use { document ->
            val page = document.pages[2]
            document.pages.filterNot { it == page }.forEach(document::removePage)
            document.essays(
                page,
                PDType0Font.load(document, fontFile.inputStream()),
                form.copy(
                    introduction = form.introduction.takeIf { introduction },
                    studyPlan = form.studyPlan.takeUnless { introduction },
                ),
                ReceiptNumber.of(form.applicantId),
            )
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    override fun renderRegistrationDocument(form: ApplicationForm, template: ByteArray): ByteArray =
        Loader.loadPDF(template).use { document ->
            val font = PDType0Font.load(document, fontFile.inputStream())
            Sheet(document, document.pages[0], font).use { it.admissionConsent(form) }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    // 깨졌거나 암호가 걸린 PDF 는 PDFBox 가 IOException 을 던진다.
    override fun isRegistrationTemplate(template: ByteArray): Boolean =
        runCatching { Loader.loadPDF(template).use { it.numberOfPages > 0 } }.getOrDefault(false)

    private fun templateOf(form: ApplicationForm) = if (form.ged) gedTemplate else template

    private fun resource(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Resource not found: $path" }.use { it.readBytes() }
}

/**
 * 서식 1 입학원서. 수험번호 칸은 서식에 "*기재하지 않음" 이 인쇄돼 있다.
 *
 * 학교코드·출신지역·출신학교와 추천서의 학교 이름은 출신 중학교 값이라 검정고시 지원자는 빈다. 출신지역은 중학교 소재지다
 * ([ApplicationForm.MiddleSchool.originRegion]).
 *
 * 검정고시 서식은 위쪽 칸과 가산점 칸의 위치가 같다. 학교 칸에는 "-", 졸업구분 칸에는 "검정고시" 가 인쇄돼 있고,
 * 교과성적 표 자리에 교과마다 점수 칸 하나인 검정고시 점수 표가 있다. 출결·추천서·원서작성자 칸은 없다.
 *
 * ponytail: 보훈번호는 원서에 저장하는 값이 없어 비운다. 수집하기로 하면 [ApplicationForm] 에 담아 찍는다.
 */
private fun Sheet.application(form: ApplicationForm, receipt: String, photo: ByteArray?) {
    val school = form.printedSchool
    val ged = form.ged
    text(109.32f, 104.88f, 219.36f, 129.00f, receipt)
    text(274.56f, 104.88f, 382.20f, 129.00f, school?.code)

    text(160.68f, 129.00f, 274.56f, 153.12f, form.name)
    text(326.64f, 129.00f, 439.80f, 153.12f, form.phoneNumber)
    text(160.68f, 153.12f, 274.56f, 177.36f, form.birthdate)
    text(326.64f, 153.12f, 439.80f, 177.36f, school?.originRegion)
    text(160.68f, 177.36f, 274.56f, 201.48f, form.gender?.label)
    text(326.64f, 177.36f, 439.80f, 201.48f, school?.name)
    if (!ged) text(160.68f, 201.48f, 439.80f, 225.60f, graduation(form))
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
    text(399.24f, 279.48f, 536.40f, 303.72f, form.specialNote ?: EMPTY_CELL)

    // 학교는 7과목, 검정고시는 역사를 제외한 6과목을 출력한다. 검정고시 미입력은 빈칸으로 둔다.
    val rows = if (ged) floatArrayOf(340.68f, 362.24f, 383.80f, 405.36f, 426.92f, 448.48f, 470.04f)
        else floatArrayOf(340.68f, 359.16f, 377.64f, 396.12f, 414.60f, 433.08f, 451.56f, 470.04f)
    val (columns, grades) = if (ged) {
        floatArrayOf(160.68f, 399.24f) to listOf(form.gedScores)
    } else {
        floatArrayOf(109.32f, 181.80f, 254.28f, 326.76f, 399.24f) to form.semesterGrades
    }
    repeat(columns.size - 1) { column ->
        val subjects = grades.getOrNull(column)?.inFormOrder()?.let { values ->
            if (ged) values.filterIndexed { index, _ -> index != 2 } else values
        }
        repeat(rows.size - 1) { row ->
            val grade = subjects?.get(row)?.takeIf { it.isNotBlank() } ?: if (ged) "" else EMPTY_CELL
            text(columns[column], rows[row], columns[column + 1], rows[row + 1], grade)
        }
    }

    // 출결 칸은 단위(일·회·시간)가 오른쪽에 인쇄돼 있어 그 왼쪽에 숫자만 찍는다.
    form.academicRecord?.let { record ->
        if (!ged) {
            text(477.72f, 322.20f, 519.00f, 340.68f, record.absentCount.toString())
            text(477.72f, 340.68f, 519.00f, 359.16f, record.lateCount.toString())
            text(477.72f, 359.16f, 519.00f, 377.64f, record.earlyLeaveCount.toString())
            text(477.72f, 377.64f, 519.00f, 396.12f, record.classAbsenceCount.toString())
            text(477.72f, 396.12f, 509.00f, 414.60f, record.volunteerTime.toString())
        }
    }
    // 가산점은 받았으면 O, 아니면 [EMPTY_CELL] 이다. 성적을 아직 넣지 않은 원서도 받지 않은 것으로 본다.
    val record = form.academicRecord
    text(477.72f, 433.08f, 536.40f, 451.56f, if (record?.dsmAlgorithmAwarded == true) "O" else EMPTY_CELL)
    text(477.72f, 451.56f, 536.40f, 470.04f, if (record?.programmingCertified == true) "O" else EMPTY_CELL)

    // 추천서의 "(      )중학교장" 괄호 사이. 인쇄는 약 12pt 지만 나눔고딕은 글자 잉크가 커서 11pt 가 인쇄 글자 높이와 맞는다.
    text(254.70f, 690.18f, 362.59f, 712.18f, school?.namePrefix(), size = 11f)

    // 원서작성자 칸에는 "교사:" 와 "(서명 또는 인)" 이 인쇄돼 있어 그 사이에 담임 이름만 찍는다.
    text(166.57f, 751.44f, 241.56f, 775.56f, school?.teacherName)
    text(399.24f, 751.44f, 536.40f, 775.56f, school?.phone)
}

/**
 * 서식 3 자기소개서·학업계획서. 본문은 지원자가 쓴 줄바꿈을 살려 칸 위부터 채운다.
 *
 * 본문이 칸을 넘치면 글자를 줄이지 않고 칸을 아래로 늘린다. 그래서 서식 원본은 자기소개서 본문 칸 위와 학업계획서
 * 안내 칸만 잘라 깔고, 본문 칸 테두리는 직접 긋는다. 칸이 서식 아래 여백에 닿으면 바로 뒤에 빈 장을 붙여 이어 쓴다.
 * 학업계획서는 남은 자리에 글이 다 들어가거나 원본 높이의 칸이 들어가면 그 장에서, 아니면 다음 장에서 시작한다.
 * 두 칸 모두 원본 높이로 긋고 긴 글만큼 늘리며, 남은 자리가 원본 높이보다 좁으면 아래 여백까지만 긋는다.
 */
private fun PDDocument.essays(page: PDPage, font: PDFont, form: ApplicationForm, receipt: String) {
    val head = band(page, 0f, INTRODUCTION_TOP)
    // 학업계획서 안내 칸은 위 틈 절반까지 자른다. 자기소개서 칸 아랫선이 따라와 다음 장 맨 위에 남지 않게 한다.
    val studyPlanHead = band(page, (INTRODUCTION_BOTTOM + STUDY_PLAN_HEAD_TOP) / 2, STUDY_PLAN_TOP)
    // 원본 글꼴은 띠가 가져갔다. 장에는 새로 찍는 것만 담는다.
    page.resources = PDResources()
    var sheet = Sheet(this, page, font, AppendMode.OVERWRITE)
    var y = INTRODUCTION_TOP
    val leading = ESSAY_FONT_SIZE * LINE_HEIGHT

    fun nextPage() {
        val next = PDPage(page.mediaBox)
        pages.insertAfter(next, sheet.page)
        sheet.close()
        sheet = Sheet(this, next, font)
        y = CONTINUED_TOP
    }

    fun wrapped(text: String?) = sheet.wrapped(text, ESSAY_FONT_SIZE, ESSAY_RIGHT - ESSAY_LEFT - 2 * PADDING_X)

    /** 줄을 다 담는 본문 칸 높이. 빈 칸도 한 줄 높이는 둔다. */
    fun height(lines: List<String>) = PADDING_X + maxOf(1, lines.size) * leading + PADDING_Y

    /** 본문 칸 하나를 [y] 부터 [minHeight] 이상 긋는다. 더 긴 글은 칸을 늘리고, 장을 넘치면 다음 장에 잇는다. */
    fun body(text: List<String>, minHeight: Float) {
        var lines = text
        var min = minHeight
        while (true) {
            val fit = ((STUDY_PLAN_BOTTOM - y - PADDING_X - PADDING_Y) / leading).toInt()
            val bottom = if (lines.size <= fit) minOf(y + maxOf(min, height(lines)), STUDY_PLAN_BOTTOM) else STUDY_PLAN_BOTTOM
            sheet.box(ESSAY_LEFT, y, ESSAY_RIGHT, bottom)
            sheet.lines(ESSAY_LEFT, ESSAY_RIGHT, y + PADDING_X, lines.take(fit), ESSAY_FONT_SIZE, Align.LEFT)
            y = bottom
            if (lines.size <= fit) return
            lines = lines.drop(fit)
            min = 0f
            nextPage()
        }
    }

    try {
        sheet.place(head, shift = 0f)
        // 성명 칸 오른쪽에는 "(서명)" 이 인쇄돼 있어 그 앞까지만 쓴다.
        sheet.personalInfo(form, receipt, rows = floatArrayOf(139.20f, 165.48f, 191.64f, 217.80f), nameRight = 278.00f)
        body(wrapped(form.introduction), INTRODUCTION_BOTTOM - INTRODUCTION_TOP)
        val studyPlan = wrapped(form.studyPlan)
        val header = STUDY_PLAN_TOP - INTRODUCTION_BOTTOM
        // 원본 칸 높이만큼 자리가 남으면 여기서 시작해 다음 장에 잇는다.
        // 0.01pt 는 자기소개서 칸이 원본 그대로일 때 부동소수 오차로 학업계획서가 넘어가지 않게 봐준다.
        if (STUDY_PLAN_BOTTOM - y - header + 0.01f < minOf(height(studyPlan), STUDY_PLAN_BOTTOM - STUDY_PLAN_TOP)) {
            // 빈 학업계획서 칸 하나 때문에 장을 늘리지 않는다. 자기소개서만 뽑을 때(renderEssay)와 작성 중인 원서다.
            if (studyPlan.isEmpty()) return
            nextPage()
        }
        sheet.place(studyPlanHead, shift = y - INTRODUCTION_BOTTOM)
        y += header
        body(studyPlan, STUDY_PLAN_BOTTOM - STUDY_PLAN_TOP)
    } finally {
        sheet.close()
    }
}

/**
 * 서식 원본 장의 [top]~[bottom] 띠만 담은 그림. 띠 밖은 그림 테두리(BBox)가 잘라 내고, 띠 밖 글자는 아예 뺀다 —
 * 잘려 보이지 않는 글자도 PDF 검색·복사에는 섞여 나온다. 글자 위치는 글자 행렬(Tm)이 놓는 기준선으로 본다.
 *
 * ponytail: 글자 묶음(BT~ET) 하나를 그 묶음의 Tm 한 곳으로 보고 통째로 넣거나 뺀다. 지금 두 서식은 묶음마다 Tm 하나에
 * 줄 이동(Td·TD·T*·'·")이 없다. 줄 이동이 있는 서식으로 갈면 테스트가 실패하니, 그때 줄 이동을 따라가며 그리는 명령마다
 * 띠 안팎을 가르도록 바꾼다.
 */
private fun PDDocument.band(page: PDPage, top: Float, bottom: Float): PDFormXObject {
    val pageHeight = page.mediaBox.height
    val kept = mutableListOf<Any>()
    var text: MutableList<Any>? = null
    var inside = true
    var ctm = Matrix()
    val saved = ArrayDeque<Matrix>()
    val operands = mutableListOf<Float>()
    PDFStreamParser(page).parse().forEach { token ->
        (text ?: kept).add(token)
        if (token !is Operator) {
            (token as? COSNumber)?.let { operands += it.floatValue() }
            return@forEach
        }
        fun matrix() = Matrix(operands[0], operands[1], operands[2], operands[3], operands[4], operands[5])
        when (token.name) {
            OperatorName.SAVE -> saved.addLast(ctm.clone())
            OperatorName.RESTORE -> ctm = saved.removeLast()
            OperatorName.CONCAT -> ctm = matrix().multiply(ctm)
            OperatorName.BEGIN_TEXT -> {
                text = mutableListOf(kept.removeAt(kept.lastIndex))
                inside = true
            }
            OperatorName.SET_MATRIX -> inside = pageHeight - matrix().multiply(ctm).translateY in top..bottom
            OperatorName.END_TEXT -> {
                if (inside) kept.addAll(text!!)
                text = null
            }
        }
        operands.clear()
    }
    val stream = PDStream(this)
    stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(kept) }
    return PDFormXObject(stream).apply {
        resources = page.resources
        bBox = PDRectangle(0f, pageHeight - bottom, page.mediaBox.width, bottom - top)
    }
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
    text(400.80f, rows[1], 535.68f, rows[2], form.printedSchool?.name, PERSONAL_INFO_FONT_SIZE)
    text(182.04f, rows[2], 535.68f, rows[3], form.address, PERSONAL_INFO_FONT_SIZE, Align.LEFT, wrap = true)
}

/**
 * 서식 4 학교장 추천서. 날짜·담임 이름은 학교가 손으로 쓰는 칸이라 비운다.
 * 접수번호와 성명은 인쇄된 "접수번호:"·"성 명 :" 뒤에 같은 크기로 이어 쓰고, 추천분야 표에는 지원한 전형 칸에만 ○ 를 찍는다.
 * 학교·반 괄호도 인쇄된 글자와 같은 크기로 괄호 사이에 찍는다.
 */
private fun Sheet.recommendation(form: ApplicationForm, receipt: String) {
    val school = form.school
    text(387.00f, 148.74f, 533.04f, 168.74f, receipt, size = 12f, align = Align.LEFT)
    text(302.90f, 210.40f, 384.39f, 234.40f, school?.namePrefix(), size = 15f)
    text(345.06f, 240.40f, 374.20f, 264.40f, school?.classNumber, size = 15f)
    text(344.00f, 270.40f, 533.04f, 294.40f, form.name, size = 15f, align = Align.LEFT)
    text(93.36f, 398.64f, 295.08f, 426.24f, "○".takeIf { form.admissionType == Applicant.AdmissionType.MEISTER }, size = 14f)
    text(295.08f, 398.64f, 496.80f, 426.24f, "○".takeIf { form.admissionType == Applicant.AdmissionType.SOCIAL }, size = 14f)
    text(171.98f, 637.83f, 299.12f, 665.83f, school?.namePrefix(), size = 18f)
}

/**
 * "(    )중학교"·"[    ] 중학교장" 괄호에 넣는 학교 이름. 괄호 뒤에 "중학교" 가 인쇄돼 있어 떼고 넣는다.
 *
 * ponytail: 기관코드 표 3,281개 중 분교장·캠퍼스 19개("원이중학교이원분교장")는 "중학교" 로 끝나지 않아 이름을 통째로 넣는다.
 * 그런 학교 지원자가 생기면 본교 이름을 넣도록 바꾼다.
 */
private fun ApplicationForm.MiddleSchool.namePrefix() = name.trim().removeSuffix("중학교")

/**
 * 서식 7 최종 합격자 제출 서류의 입학 동의서. 인쇄된 라벨과 같은 11pt 로 찍는다.
 * 날짜·서명과 개인정보 동의 체크는 지원자·보호자가 손으로 쓰는 칸이라 비운다.
 *
 * ponytail: 좌표는 2027학년도 서식 7 에서 쟀다. 관리자가 모양이 다른 PDF 를 올리면 칸이 어긋나니 서식이 바뀌면 다시 잰다.
 */
private fun Sheet.admissionConsent(form: ApplicationForm) {
    val size = PERSONAL_INFO_FONT_SIZE
    text(407.16f, 123.60f, 538.68f, 144.72f, form.examineeNumber, size)
    text(157.92f, 144.72f, 318.84f, 170.64f, form.name, size)
    // 인쇄된 "□남 □여" 의 네모에 체크 표시를 한다.
    when (form.gender) {
        ApplicationForm.Gender.MALE -> check(443.88f, 152.04f, 453.69f, 163.08f)
        ApplicationForm.Gender.FEMALE -> check(481.09f, 152.04f, 490.90f, 163.08f)
        null -> Unit
    }
    text(157.92f, 170.64f, 318.84f, 196.44f, form.phoneNumber, size)
    // 생년월일 칸에는 "년 월 일" 이 인쇄돼 있어 각 글자 앞에 숫자만 찍는다.
    form.birthdate?.split('-')?.takeIf { it.size == 3 }?.let { (year, month, day) ->
        text(407.16f, 170.64f, 464.88f, 196.44f, year, size)
        text(475.92f, 170.64f, 492.37f, 196.44f, month.trimStart('0'), size)
        text(503.41f, 170.64f, 519.86f, 196.44f, day.trimStart('0'), size)
    }
    text(157.92f, 196.44f, 538.68f, 222.36f, form.address, size, Align.LEFT, wrap = true)
    text(157.92f, 222.36f, 318.84f, 248.88f, form.guardianName, size)
    // "지원자의 (      )" 괄호 사이.
    text(475.75f, 222.36f, 519.47f, 248.88f, form.guardianRelation, size)
    text(157.92f, 248.88f, 538.68f, 274.80f, form.guardianPhoneNumber, size)
}

/** 검정고시 지원자는 서식이 따로 있다([GED_TEMPLATE_RESOURCE]). */
private val ApplicationForm.ged get() = graduationType == ApplicationForm.GraduationType.GED

/**
 * 원서에 찍을 출신 중학교. 검정고시 서식은 학교 칸마다 "-" 가 인쇄돼 있어 찍지 않는다.
 * #346 전에는 검정고시로 바꾼 뒤에도 학교를 저장할 수 있어서, 그때 남은 값이 있어도 버린다.
 */
private val ApplicationForm.printedSchool get() = school.takeUnless { ged }

/** 졸업구분 칸. "졸업예정 (2027-02)" 처럼 구분 뒤에 졸업 연월을 붙인다. */
private fun graduation(form: ApplicationForm): String? {
    val type = form.graduationType?.label ?: return form.graduationDate
    return form.graduationDate?.let { "$type ($it)" } ?: type
}

private enum class Align { LEFT, CENTER }

/**
 * 서식 한 장에 값을 덧찍는다. [AppendMode.OVERWRITE] 로 열면 원본 내용을 지우고, 필요한 띠만 [original] 로 다시 깐다.
 */
private class Sheet(
    private val document: PDDocument,
    val page: PDPage,
    private val font: PDFont,
    mode: AppendMode = AppendMode.APPEND,
) : AutoCloseable {

    private val pageHeight = page.mediaBox.height

    // resetContext: 원본 내용이 좌표계를 바꿔 둔 채 끝나도 기본 좌표계에서 찍는다.
    private val stream = PDPageContentStream(document, page, mode, true, true)

    /**
     * 칸 안에 값을 찍는다. 칸을 넘치면 글자를 [MIN_FONT_SIZE] 까지 줄인다. [wrap] 칸은 칸 폭에서 줄을 바꾸고 줄 수가
     * 칸 높이를 넘칠 때 줄이며, 그래도 넘치는 줄은 버린다. 줄 묶음은 칸 가운데에 둔다.
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
    ) {
        val text = value?.takeIf { it.isNotBlank() }?.let { printable(it) } ?: return
        val width = right - left - 2 * PADDING_X
        val height = bottom - top - 2 * PADDING_Y
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
        lines(left, right, (top + bottom - lines.size * leading) / 2, lines, fontSize, align)
    }

    /** 이미 나눈 줄을 [blockTop] 부터 한 줄씩 찍는다. [left]·[right] 는 줄을 맞출 칸의 좌우다. */
    fun lines(left: Float, right: Float, blockTop: Float, lines: List<String>, fontSize: Float, align: Align) {
        if (lines.isEmpty()) return
        val leading = fontSize * LINE_HEIGHT
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

    /** 칸 테두리를 서식 원본과 같은 굵기로 긋는다. */
    fun box(left: Float, top: Float, right: Float, bottom: Float) {
        stream.saveGraphicsState()
        stream.setLineWidth(BORDER_WIDTH)
        stream.addRect(left, pageHeight - bottom, right - left, bottom - top)
        stream.stroke()
        stream.restoreGraphicsState()
    }

    /** 서식 원본에서 잘라 낸 띠([band])를 [shift] 만큼 아래로 옮겨 깐다. */
    fun place(band: PDFormXObject, shift: Float) {
        stream.saveGraphicsState()
        stream.transform(Matrix.getTranslateInstance(0f, -shift))
        stream.drawForm(band)
        stream.restoreGraphicsState()
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

    /** 인쇄된 네모(□) 안에 체크(✓) 를 긋는다. 글꼴에 ✓ 가 없어 선으로 그린다. 좌표는 네모 글자의 칸이다. */
    fun check(left: Float, top: Float, right: Float, bottom: Float) {
        val width = right - left
        // 글자 칸 위아래에 여백이 있어 네모는 칸 가운데의 정사각형이다.
        val boxTop = (top + bottom - width) / 2
        fun y(fromTop: Float) = pageHeight - (boxTop + fromTop * width)
        stream.saveGraphicsState()
        stream.setLineWidth(1.2f)
        stream.moveTo(left + 0.15f * width, y(0.5f))
        stream.lineTo(left + 0.4f * width, y(0.8f))
        stream.lineTo(left + 0.9f * width, y(0.1f))
        stream.stroke()
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

    /** 빈 값은 줄이 없다. 찍지 못하는 글자는 [printable] 로 바꿔 나눈다. */
    fun wrapped(value: String?, fontSize: Float, width: Float): List<String> =
        value?.takeIf { it.isNotBlank() }?.let { wrap(printable(it), fontSize, width) }.orEmpty()

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
