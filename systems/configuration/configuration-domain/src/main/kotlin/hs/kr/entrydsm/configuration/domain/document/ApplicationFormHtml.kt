package hs.kr.entrydsm.configuration.domain.document

private const val SCHOOL_NAME = "대덕소프트웨어마이스터고등학교"
private const val PRINCIPAL_LINE = "대덕소프트웨어마이스터고등학교장 귀하"

/** 서식의 교과 행. 요강 6쪽이 나열한 7개 과목과 순서가 같다. */
private val SUBJECT_LABELS = listOf("국어", "사회", "역사", "수학", "과학", "기술·가정", "영어")

/** 서식의 교과성적 표 열 이름. 자유학기를 건너뛴 상대 순서라 실제 학기는 지원자마다 다르다. */
private val SEMESTER_LABELS = listOf("3학년 2학기", "3학년 1학기", "직전학기", "직전전학기")

/**
 * 요강 <서식 1> 입학원서를 XHTML 로 만든다. PDF 변환기(openhtmltopdf)는 well-formed XHTML 만 받으므로 태그를 모두 닫고
 * 엔티티도 숫자 참조(`&#160;`)만 쓴다.
 *
 * 폭은 24 등분 격자 하나로 맞춘다. 블록마다 열 구성이 달라 colspan 으로 나눈다.
 *
 * ponytail: 요강의 `기술∙가정`(U+2219)은 NanumGothic 에 글리프가 없어 `#` 로 찍힌다. `기술·가정`(U+00B7)을 쓴다.
 * 폰트를 바꾸면 요강 표기를 그대로 쓸 수 있다.
 *
 * ponytail: 학교코드·보훈번호·출신지역은 원서에 저장하는 값이 없어 빈칸으로 둔다. 수집하기로 하면
 * [ApplicationForm] 에 담아 넣는다.
 */
object ApplicationFormHtml {

    /** PDF 어댑터가 한글 폰트를 이 이름으로 등록한다. */
    const val FONT_FAMILY = AdmissionTicketHtml.FONT_FAMILY

    fun render(admissionYear: Int, form: ApplicationForm, photoDataUri: String?): String {
        // 서식의 서명·날인 연도는 입학 학년도의 전 해다.
        val signatureYear = admissionYear - 1

        return """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
              <title>입학원서</title>
              <style>
                @page { size: A4; margin: 12mm 12mm; }
                body { font-family: "$FONT_FAMILY"; font-size: 8pt; }
                h1 { font-size: 15pt; text-align: center; margin: 0 0 4mm 0; }
                .caption { font-size: 8pt; margin: 0 0 2mm 0; }
                table { width: 100%; border-collapse: collapse; table-layout: fixed; }
                td { border: 1px solid #000000; padding: 1.2mm 1.5mm; height: 5.6mm; }
                .label { background-color: #efefef; text-align: center; }
                .value { text-align: center; }
                .left { text-align: left; }
                .head { background-color: #efefef; text-align: center; font-weight: bold; }
                .photo { text-align: center; vertical-align: middle; }
                .photo img { display: block; width: 100%; }
                .pledge { text-align: left; vertical-align: top; height: 34mm; }
                .sign { text-align: center; font-size: 10pt; font-weight: bold; }
                .recommend { text-align: left; vertical-align: top; height: 30mm; }
              </style>
            </head>
            <body>
              <p class="caption">&lt;서식 1&gt; (인터넷접수 후 출력)</p>
              <h1>${escape("${admissionYear}학년도 $SCHOOL_NAME 입학원서")}</h1>
              <table>
                <colgroup>${"<col />".repeat(24)}</colgroup>
                ${identityRow(form)}
                ${applicantRows(form, photoDataUri)}
                ${guardianRow(form)}
                ${screeningRow(form)}
                ${gradeRows(form)}
                ${pledgeRow(signatureYear)}
                ${recommendationRow(signatureYear)}
                ${authorRow(form)}
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    /** 학교코드는 원서에 없어 비운다. 수험번호는 서식이 "기재하지 않음"이라고 적어 뒀다. */
    private fun identityRow(form: ApplicationForm) = """
        <tr>
          ${label("접수번호", 3)}${value(form.applicantId.toString(), 5)}
          ${label("학교코드", 3)}${value("", 5)}
          ${label("수험번호", 3)}${value("*기재하지 않음", 5)}
        </tr>
    """.trimIndent()

    /** 출신지역은 요강이 정의하지 않고 원서에 저장하는 값도 없어 비운다. */
    private fun applicantRows(form: ApplicationForm, photoDataUri: String?) = """
        <tr>
          <td class="label" rowspan="5">지원자</td>
          ${label("성명", 3)}${value(form.name, 5)}${label("전화번호", 3)}${value(form.phoneNumber, 5)}
          <td class="photo" rowspan="5" colspan="5">${photoCell(photoDataUri)}</td>
        </tr>
        <tr>
          ${label("생년월일", 3)}${value(form.birthdate, 5)}${label("출신지역", 3)}${value("", 5)}
        </tr>
        <tr>
          ${label("성별", 3)}${value(form.gender?.label, 5)}${label("출신학교", 3)}${value(form.school?.name, 5)}
        </tr>
        <tr>
          ${label("졸업구분", 3)}${value(graduationText(form), 13)}
        </tr>
        <tr>
          ${label("주소", 3)}${leftValue(form.address, 13)}
        </tr>
    """.trimIndent()

    private fun guardianRow(form: ApplicationForm) = """
        <tr>
          ${label("보호자", 3)}${label("성명", 3)}${value(form.guardianName, 6)}
          ${label("관계", 3)}${value(form.guardianRelation, 3)}
          ${label("휴대전화", 3)}${value(form.guardianPhoneNumber, 3)}
        </tr>
    """.trimIndent()

    private fun screeningRow(form: ApplicationForm) = """
        <tr>
          ${label("지역", 3)}${value(form.region?.label, 3)}
          ${label("전형유형", 4)}${value(form.admissionType?.label, 7)}
          ${label("특기사항", 4)}${value(form.specialNote, 3)}
        </tr>
    """.trimIndent()

    /**
     * 좌측 교과성적 표(5열)와 우측 출결·봉사·가산점 표(2열)를 같은 행에 붙인다. 좌측 8행·우측 8행으로 줄 수가 맞다.
     * 검정고시 지원자는 교과 성적이 없어 좌측이 통째로 빈다.
     */
    private fun gradeRows(form: ApplicationForm): String {
        val record = form.academicRecord
        val subjectValues = SUBJECT_LABELS.indices.map { row ->
            form.semesterGrades.take(ApplicationForm.SEMESTER_COLUMN_COUNT)
                .map { it?.inFormOrder()?.getOrNull(row).orEmpty() }
        }
        val rightRows = listOf(
            attendanceCells("미인정결석", record?.absentCount, "일"),
            attendanceCells("미인정지각", record?.lateCount, "회"),
            attendanceCells("미인정조퇴", record?.earlyLeaveCount, "회"),
            attendanceCells("미인정결과", record?.classAbsenceCount, "회"),
            attendanceCells("봉사활동 시간", record?.volunteerTime, "시간"),
            """<td class="head" colspan="9">가산점</td>""",
            markCells("DSM알고리즘대회", record?.dsmAlgorithmAwarded),
            markCells("정보처리기능사", record?.programmingCertified),
        )
        val leftRows = listOf(
            SEMESTER_LABELS.joinToString("") { label(it, 3) }.let { label("교과", 3) + it },
        ) + SUBJECT_LABELS.mapIndexed { row, subject ->
            label(subject, 3) + subjectValues[row].joinToString("") { value(it, 3) }
        }

        val header = """
            <tr>
              <td class="head" colspan="15">교과성적(성취도)</td>
              <td class="head" colspan="9">출결 및 봉사활동</td>
            </tr>
        """.trimIndent()

        return header + leftRows.indices.joinToString("") { "<tr>${leftRows[it]}${rightRows[it]}</tr>" }
    }

    private fun attendanceCells(label: String, count: Int?, unit: String) =
        label(label, 6) + value(count?.let { "$it$unit" }, 3)

    private fun markCells(label: String, awarded: Boolean?) =
        label(label, 6) + value(if (awarded == true) "O" else "", 3)

    /** 좌측은 지원 서약, 우측은 국가유공자 자녀 확인란이다. 보훈번호는 원서에 없어 비운다. */
    private fun pledgeRow(signatureYear: Int) = """
        <tr>
          <td class="pledge" colspan="16">
            본인은 귀 고등학교에 입학하고자 소정의 서류를 갖추어 지원합니다.<br />
            <br />
            &#160;&#160;&#160;&#160;&#160;&#160;${signatureYear}년&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;일<br />
            <br />
            &#160;&#160;&#160;&#160;지원자 :&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;(서명 또는 인)<br />
            &#160;&#160;&#160;&#160;보호자 :&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;(서명 또는 인)<br />
            <br />
            <span class="sign">$PRINCIPAL_LINE</span>
          </td>
          <td class="pledge" colspan="8">
            보훈번호:(&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;)<br />
            <br />
            위 학생은 국가유공자자녀임을 확인함.<br />
            <br />
            &#160;&#160;&#160;&#160;${signatureYear}년&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;일<br />
            <br />
            교사:&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;(서명 또는 인)
          </td>
        </tr>
    """.trimIndent()

    private fun recommendationRow(signatureYear: Int) = """
        <tr>
          <td class="recommend" colspan="24">
            <span class="sign">추&#160;&#160;&#160;&#160;천&#160;&#160;&#160;&#160;서</span><br />
            본 입학원서의 내용은 사실과 다름이 없으며, 위 학생은 귀교에 입학 적격자로 인정되므로 추천합니다.<br />
            <br />
            &#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;${signatureYear}년&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;일<br />
            &#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;(&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;)중학교장&#160;&#160;(직인)<br />
            <br />
            <span class="sign">$PRINCIPAL_LINE</span>
          </td>
        </tr>
    """.trimIndent()

    private fun authorRow(form: ApplicationForm) = """
        <tr>
          ${label("원서작성자", 4)}
          ${leftValue(form.school?.teacherName?.let { "교사: $it" } ?: "교사:", 11)}
          ${label("연락처", 4)}${value(form.school?.phone, 5)}
        </tr>
    """.trimIndent()

    private fun graduationText(form: ApplicationForm): String? {
        val type = form.graduationType?.label ?: return form.graduationDate
        return form.graduationDate?.let { "$type ($it)" } ?: type
    }

    private fun label(text: String, span: Int) = """<td class="label" colspan="$span">${escape(text)}</td>"""

    private fun value(text: String?, span: Int) = """<td class="value" colspan="$span">${escape(text.orEmpty())}</td>"""

    private fun leftValue(text: String?, span: Int) = """<td class="left" colspan="$span">${escape(text.orEmpty())}</td>"""

    private fun photoCell(photoDataUri: String?): String =
        photoDataUri?.let { """<img src="${escape(it)}" alt="사진" />""" } ?: "사진<br />(3cm×4cm)"

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
