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
                td { border: 1px solid #000000; padding: 1.2mm 1.5mm; height: 7.5mm; }
                .label { background-color: #efefef; text-align: center; }
                .value { text-align: center; }
                .left { text-align: left; }
                /* 출결 표의 값과 단위. 요강은 한 칸처럼 보이므로 맞닿은 테두리를 지운다. */
                .count { text-align: center; border-right: 0; }
                .unit { text-align: right; border-left: 0; }
                .head { background-color: #efefef; text-align: center; font-weight: bold; }
                .photo { text-align: center; vertical-align: middle; }
                .photo img { display: block; width: 30mm; height: 40mm; margin: 0 auto; }
                .pledge { text-align: left; vertical-align: top; height: 52mm; }
                .sign { font-size: 10pt; font-weight: bold; }
                .title { text-align: center; font-size: 10pt; font-weight: bold; }
                .recommend { text-align: left; vertical-align: top; height: 56mm; }
                /* 서식 하나가 A4 한 장이다. 둘째 서식부터 앞에서 쪽을 끊는다. */
                .sheet { page-break-before: always; }
              </style>
            </head>
            <body>
              ${applicationPage(admissionYear, signatureYear, form, photoDataUri)}
              ${privacyConsentPage(signatureYear)}
              ${selfIntroductionPage(form)}
              ${principalRecommendationPage(admissionYear, form)}
              ${nonSmokingConsentPage(signatureYear, form)}
              ${smokingTestConsentPage(signatureYear, form)}
            </body>
            </html>
        """.trimIndent()
    }

    /** 요강 <서식 1> 입학원서. 쪽 나눔이 필요 없는 첫 장이라 .sheet 를 두르지 않는다. */
    private fun applicationPage(
        admissionYear: Int,
        signatureYear: Int,
        form: ApplicationForm,
        photoDataUri: String?,
    ) = """
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
    """.trimIndent()

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
          <td class="label" rowspan="5" colspan="3">지원자</td>
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
          ${label("보호자", 3)}${label("성명", 3)}${value(form.guardianName, 5)}
          ${label("관계", 2)}${value(form.guardianRelation, 3)}
          ${label("휴대전화", 3)}${value(form.guardianPhoneNumber, 5)}
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

    /** 요강은 단위(일·회·시간)를 칸 오른쪽에 미리 찍어 두고 숫자를 그 왼쪽에 쓴다. */
    private fun attendanceCells(label: String, count: Int?, unit: String) =
        label(label, 5) +
            """<td class="count" colspan="2">${escape(count?.toString().orEmpty())}</td>""" +
            """<td class="unit" colspan="2">${escape(unit)}</td>"""

    private fun markCells(label: String, awarded: Boolean?) =
        label(label, 5) + value(if (awarded == true) "O" else "", 4)

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
            <div class="sign">$PRINCIPAL_LINE</div>
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
            <div class="title">추&#160;&#160;&#160;&#160;천&#160;&#160;&#160;&#160;서</div>
            본 입학원서의 내용은 사실과 다름이 없으며, 위 학생은 귀교에 입학 적격자로 인정되므로 추천합니다.<br />
            <br />
            &#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;${signatureYear}년&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;일<br />
            &#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;(&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;)중학교장&#160;&#160;(직인)<br />
            <br />
            <div class="sign">$PRINCIPAL_LINE</div>
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

    /**
     * 요강 `<서식 2>` 개인정보 수집 및 이용 동의서다. 원서에서 가져올 값이 하나도 없는 고정 문구라 인자를 받지 않는다.
     *
     * 서식 1 과 같은 24 등분 격자를 쓰되 라벨 4 : 내용 20 으로만 나눈다. 두 표의 라벨 폭을 맞춰야 위아래가
     * 한 서식으로 읽힌다.
     *
     * 문구는 요강 원문 그대로다. 요강이 굵게 찍은 구절만 굵게 살리고, 동의 체크박스·날짜·성명은 출력 후
     * 손으로 적는 칸이라 비워 둔다.
     *
     * ponytail: 요강의 가운뎃점은 U+2024(`․`)로 찍혀 있지만 NanumGothic 글리프가 없어 `#` 로 나온다.
     * 서식 1 과 같이 U+00B7(`·`)로 바꿔 쓴다.
     *
     */
    private fun privacyConsentPage(signatureYear: Int): String {
        val consentRows = listOf(
            "근거" to "초·중등교육법 제47조 및 동법 시행령 제81조, 제82조, 제84조, 제98조 및 본교의 입학 전형 실시 계획",
            "정보주체의 권리" to
                "지원자는 자신이 제공한 개인 정보에 대하여 개인정보 보호법 제4조 및 제35조부터 제38조까지에 따라 " +
                "열람·처리·정지·정정·삭제·파기 등을 요구할 수 있으며, 개인정보 보호법을 위반한 행위로 인한 손해 발생 " +
                "시에는 개인정보 보호법 제39조에 따라 손해배상을 청구할 수 있음",
            "수집항목" to "성명, 생년월일, 증명사진, 주소, 전화번호, 학력, 출결사항, 교과성적, 봉사활동사항",
            "개인정보의<br />수집·이용목적" to
                "수집한 지원자의 개인정보는 <b>원서접수, 지원자격·지원결격사유, 지원자 본인확인, 성적 산출, " +
                "합격자 명부 관리, 합격 증명서 발급, 성적 통지, 통계자료 산출</b> 등 입학관리 업무를 위한 정보로 이용",
            "보유기간 및<br />이용기간" to
                "<b>입학관리 업무를 계속하는 동안 보유·이용</b>할 수 있으며, " +
                "<b>입학관리 업무 완료 후 5년간 보관의 목적</b>으로만 관리",
            "동의 거부" to
                "지원자는 개인정보의 수집·이용·제공에 대한 동의를 거부할 수 있으며, 동의를 거부할 경우 지원결격 사유 " +
                "조회 등 입학관리 업무를 수행할 수 없으므로 원서를 접수할 수 없음",
        )
        val thirdPartyRows = listOf(
            "제공받는 자" to "한국교육개발원, 관련기관(지원자가 졸업한 중학교 등)",
            "제공 목적" to
                "(한국교육개발원) 전기고·후기고 이중지원자 확인, 불합격자의 시도교육청 평준화 일반고 배정 지원<br />" +
                "(관련기관) 지원자격·지원결격 사유 조회 및 교과성적 확인",
            "근거" to
                "초·중등교육법 제30조의6, 제47조 및 동법 시행령 제81조, 제82조, 제84조, 제85조, 제86조, 제98조",
            "제공 항목" to
                "(한국교육개발원) 성명, 생년월일, 출신중학교명, 중학교 소속지역, 학년·반·번호, 지원고등학교명, " +
                "출신지역, 학격·불합격여부<br />" +
                "(관련기관) 성명, 생년월일, 주소, 전화번호, 학력, 출결사항, 교과성적, 봉사활동사항",
            "보유·이용 기간" to "(업무 처리 후) 1년",
        )

        // 라벨에 줄바꿈이 들어가 label() 을 못 쓴다. 두 표 모두 같은 4 : 20 격자다.
        fun rows(items: List<Pair<String, String>>) = items.joinToString("") { (label, body) ->
            """<tr><td class="label" colspan="4">$label</td><td class="left" colspan="20">$body</td></tr>"""
        }

        val gap = "&#160;".repeat(8)
        val signGap = "&#160;".repeat(15)

        return """
            <div class="sheet">
              <p class="caption">&lt;서식 2&gt; (인터넷접수 후 출력)</p>
              <h1>개인정보 수집 및 이용 동의서</h1>
              <div style="border: 1px solid #000000; padding: 2mm;">
                <p style="margin: 0 0 2mm 0; text-align: justify;">
                  본 입학원서에 기재된 지원자의 개인정보는 <b>신입생 입학관리</b> 업무의 원활한 수행을 위하여
                  개인정보의 수집·유출·오용·남용으로부터 사생활의 비밀 등을 보호하도록 한 개인정보보호법 규정에
                  따라 다음과 같이 수집·이용·제공됩니다.
                </p>
                <table>
                  <colgroup>${"<col />".repeat(24)}</colgroup>
                  ${rows(consentRows)}
                </table>
              </div>
              <p style="margin: 3mm 0 1mm 0;">&#9654; 제3자 제공</p>
              <table>
                <colgroup>${"<col />".repeat(24)}</colgroup>
                ${rows(thirdPartyRows)}
              </table>
              <table style="margin-top: 3mm; font-size: 10pt;">
                <colgroup>${"<col />".repeat(24)}</colgroup>
                <tr>
                  <td class="left" colspan="10" style="border-right: 0;">개인정보 수집·이용 동의</td>
                  <td class="value" colspan="7" style="border-left: 0; border-right: 0;">&#9633; 예</td>
                  <td class="value" colspan="7" style="border-left: 0;">&#9633; 아니요</td>
                </tr>
              </table>
              <div style="margin-top: 12mm; font-size: 10pt;">
                <div style="text-align: center;">${signatureYear}년${gap}월${gap}일</div>
                <div style="text-align: right; padding-right: 4mm; margin-top: 4mm;">
                  지원자 성명:$signGap(서명 또는 인)
                </div>
                <div style="text-align: right; padding-right: 4mm; margin-top: 4mm;">
                  보호자 성명:$signGap(서명 또는 인)
                </div>
              </div>
              <div class="sign" style="margin-top: 12mm;">$PRINCIPAL_LINE</div>
            </div>
        """.trimIndent()
    }

    /**
     * 요강 <서식 3> 자기소개서 및 학업계획서 한 장을 그린다.
     *
     * 인적사항 표만 값을 채운다. 성명 칸의 "(서명)" 은 원서에 인쇄된 문구라 값 오른쪽에 그대로 둔다.
     * 값과 인쇄 문구를 한 칸처럼 붙이는 짝은 출결 표에서 쓰던 `.count`/`.unit`(맞닿은 테두리를 지운
     * 짝) 이 이미 하는 일이라 그대로 재사용한다.
     *
     * 본문은 지원자가 직접 쓴 글이라 줄바꿈이 뜻을 가진다. escape() 한 뒤 줄 단위로 `<br />` 를 넣는다.
     * `lines()` 를 쓰면 `\r\n` 로 들어온 글도 같이 끊긴다.
     *
     * 빈칸 포함 1,600자가 다 차도 한 장을 넘지 않아야 한다. 본문을 7pt 로 두면 183mm 짜리 줄에 한글이
     * 74자쯤 들어가 1,600자는 22줄, 문단 나눔까지 넉넉히 잡아도 28줄이다. 그래서 본문 칸 높이를 88mm
     * (28줄 × 3.1mm) 로 고정했다. 머리말·인적사항 약 68mm + 88mm × 2 = 244mm 로 A4 인쇄영역 273mm 안이다.
     * 표 칸 높이는 최소값이라 이보다 긴 글이 오면 칸이 늘어나 두 장이 된다 — 글자 수 제한은 입력 쪽에서 건다.
     *
     * ponytail: 요강의 `∙`(U+2219)은 NanumGothic 에 글리프가 없어 `·`(U+00B7)로 바꿔 적었다. 서식 1 과 같은 이유다.
     */
    private fun selfIntroductionPage(form: ApplicationForm): String {
        // 두 서식(자기소개서·학업계획서)은 안내문 칸 + 본문 칸으로 구조가 같아 한 틀로 찍는다.
        fun essay(heading: String, guide: String, body: String?) = """
            <table style="margin-top: 2mm;">
              <tr>
                <td class="left" colspan="24">◎ <strong>${escape(heading)}</strong>${escape(guide)}</td>
              </tr>
              <tr>
                <td class="left" colspan="24" style="height: 88mm; vertical-align: top; font-size: 7pt; line-height: 1.25;">
                  (빈칸 포함 1,600자 이내)<br />
                  ${escape(body.orEmpty()).lines().joinToString("<br />")}
                </td>
              </tr>
            </table>
        """.trimIndent()

        return """
            <div class="sheet">
            <p class="caption">&lt;서식 3&gt; (인터넷접수 후 출력)</p>
            <h1>자기소개서 및 학업계획서</h1>
            <table>
              <colgroup>${"<col />".repeat(24)}</colgroup>
              <tr>
                <td class="label" rowspan="3" colspan="3">인적<br />사항</td>
                <td class="label" colspan="4">성&#160;&#160;&#160;명</td>
                <td class="count" colspan="4">${escape(form.name.orEmpty())}</td>
                <td class="unit" colspan="3">(서명)</td>
                ${label("접수번호", 4)}${value(form.applicantId.toString(), 6)}
              </tr>
              <tr>
                ${label("전화번호", 4)}${value(form.phoneNumber, 7)}
                ${label("출신학교", 4)}${value(form.school?.name, 6)}
              </tr>
              <tr>
                <td class="label" colspan="4">주&#160;&#160;&#160;소</td>
                ${leftValue(form.address, 17)}
              </tr>
            </table>
            ${essay(
                "자기소개서",
                " 내용은 특별한 형식이 없으며 개인의 특성 및 성장 과정, 취미 · 특기, 학교 생활, " +
                    "가족 안에서의 역할, 남들보다 뛰어나다고 생각하는 자신의 장점(특성 혹은 능력)과 " +
                    "보완 · 발전시켜야 할 단점에 대하여 기술하십시오.",
                form.introduction,
            )}
            ${essay(
                "학업계획서",
                "는 자신이 본교를 선택하게 된 구체적인 사유(지원 동기)와 고등학생이 된 후 이루고자 " +
                    "하는 목표를 달성하기 위한 학업계획을 상세하게 기술하십시오.",
                form.studyPlan,
            )}
            </div>
        """.trimIndent()
    }

    /**
     * 요강 <서식 4> 학교장 추천서 한 장. 특별전형 지원자만 내는 서식이라 추천분야 표에는 지원한 전형 칸에만 ○ 를
     * 찍고, 일반전형이거나 아직 고르지 않았으면 두 칸 다 비운다.
     *
     * 학교·반·날짜·담임 이름은 원본이 손으로 쓰게 비워 둔 칸이라 인쇄 문구만 그린다. 값을 채우는 곳은
     * 접수번호와 성명뿐이다.
     *
     * 24 등분 격자를 쓰는 render() 의 본문 표와 달리 이 장은 테두리 굵은 상자 하나뿐이라 한 칸짜리 표로 둔다.
     * 상자 안의 줄 간격은 요강 쪽에서 잰 위치를 mm 로 옮긴 값이라 인라인 스타일로 적는다 — 이 장에서만 쓰는
     * 간격이라 클래스로 뽑을 이유가 없다.
     */
    private fun principalRecommendationPage(admissionYear: Int, form: ApplicationForm): String {
        // 서식에 인쇄된 날짜 골격은 입학 학년도의 전 해다. render() 의 signatureYear 와 같은 규칙이다.
        val signatureYear = admissionYear - 1
        val meisterMark = if (form.admissionType == Applicant.AdmissionType.MEISTER) "○" else ""
        val socialMark = if (form.admissionType == Applicant.AdmissionType.SOCIAL) "○" else ""

        return """
            <div class="sheet">
              <p class="caption">&lt;서식 4&gt; (인터넷접수 후 출력)</p>
              <h1>학&#160;교&#160;장&#160;&#160;추&#160;천&#160;서</h1>
              <div style="margin: 8mm 0 6mm 55%;">${escape("접수번호: ${form.applicantId}")}</div>
              <table>
                <tr>
                  <td style="border: 2px solid #000000; height: 205mm; vertical-align: top; padding: 20mm 10mm 0 10mm; font-size: 11pt;">
                    <div style="padding-left: 66mm; font-weight: bold; line-height: 2.4;">
                      (&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;)중학교<br />
                      3학년 (&#160;&#160;&#160;&#160;&#160;&#160;&#160;)반<br />
                      성&#160;명 : ${escape(form.name.orEmpty())}
                    </div>
                    <div class="title" style="margin: 18mm 0 3mm 0;">특별전형 추천분야</div>
                    <table style="width: 75%; margin: 0 auto;">
                      <colgroup><col /><col /></colgroup>
                      <tr>${value("마이스터 인재 전형", 1)}${value("사회통합 전형", 1)}</tr>
                      <tr>${value(meisterMark, 1)}${value(socialMark, 1)}</tr>
                    </table>
                    <div style="margin: 12mm 0 0 0; text-indent: 8mm; line-height: 1.9;">${escape("위 학생을 ${admissionYear}학년도 $SCHOOL_NAME 특별전형 대상자로 추천합니다.")}</div>
                    <div style="margin: 16mm 0 0 0; text-align: center;">${signatureYear}년&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;일</div>
                    <div style="margin: 10mm 0 0 44mm;">작성자 담임교사 :&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;<span style="font-size: 8pt;">(서명 또는 인)</span></div>
                    <div style="margin: 16mm 0 0 32mm;">[&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;] 중학교장&#160;<span style="font-size: 8pt;">(직인)</span></div>
                    <div class="sign" style="margin: 16mm 0 0 0;">$PRINCIPAL_LINE</div>
                  </td>
                </tr>
              </table>
            </div>
        """.trimIndent()
    }

    /**
     * 요강 <서식 5> 금연 동의서. 인적사항 표만 원서 값으로 채우고 서약·동의 문구는 요강 문장을 그대로 찍는다.
     *
     * 요강은 인적사항 표부터 아래까지를 상자 한 겹으로 두른다. 그래서 표 하나의 마지막 행을 본문 칸 하나로
     * 만들어 그 테두리가 곧 바깥 상자가 되게 했다. 본문 칸 200mm 는 상자가 요강만큼 내려오면서도
     * A4 한 장을 넘지 않는 높이다.
     *
     * 요강 5쪽 인적사항 칸에는 음영이 없어 [label] 대신 [value] 로 찍는다. 음영은 개인정보 표 머리행뿐이다.
     *
     */
    private fun nonSmokingConsentPage(signatureYear: Int, form: ApplicationForm) = """
        <div class="sheet">
          <p class="caption">&lt;서식 5&gt; (인터넷접수 후 출력)</p>
          <h1>금연 동의서</h1>
          <table>
            <colgroup>${"<col />".repeat(24)}</colgroup>
            <tr>
              <td class="value" rowspan="3" colspan="2">인적<br />사항</td>
              ${value("성 명", 4)}${value(form.name, 7)}
              ${value("접수번호", 4)}${value(form.applicantId.toString(), 7)}
            </tr>
            <tr>
              ${value("전화번호", 4)}${value(form.phoneNumber, 7)}
              ${value("출신학교", 4)}${value(form.school?.name, 7)}
            </tr>
            <tr>
              ${value("주 소", 4)}${leftValue(form.address, 18)}
            </tr>
            <tr>
              <td colspan="24" style="height: 200mm; padding: 6mm 5mm; vertical-align: top; text-align: left; font-size: 10pt; line-height: 1.9;">
                &#160;&#160;하나, 나 자신의 건강을 위해서 흡연하지 않겠습니다.<br />
                &#160;&#160;하나, 흡연의 유혹에 절대로 흔들리지 않겠습니다.<br />
                &#160;&#160;하나, 흡연하는 친구가 있으면 충고하여 금연할 수 있도록 돕겠습니다.<br />
                <br />
                &#160;&#160;&#160;&#160;나 (${"&#160;".repeat(16)})은(는) 장차 소프트웨어 분야를 선도할 전문가로 성장하기 위하여 흡연하지 않겠습니다.<br />
                &#160;&#160;&#160;&#160;보호자는 지원자가 금연하는 데 용기와 도움을 줄 것을 약속합니다.<br />
                <br />
                <div class="sign">&#160;&#160;□ 개인정보 수집·이용 동의</div>
                <table style="width: 96%; margin: 2mm 0 2mm 2%;">
                  <tr>
                    <td class="label" style="width: 45%;">항 목</td>
                    <td class="label" style="width: 27.5%;">수집목적</td>
                    <td class="label" style="width: 27.5%;">보유기간</td>
                  </tr>
                  <tr>
                    <td class="value">학생(성명, 연락처,<br />출신 중학교, 주소)</td>
                    <td class="value">금연 동의</td>
                    <td class="value" style="font-weight: bold;">3년</td>
                  </tr>
                </table>
                &#160;&#160;※ 개인정보 수집·이용에 대한 동의를 거부할 권리가 있습니다. 그러나 동의를 거부할 경우<br />
                &#160;&#160;&#160;&#160;최종 입학에 제한을 받을 수 있습니다.<br />
                <table style="width: 96%; margin: 6mm 0 0 2%;">
                  <tr>
                    <td class="left" colspan="12" style="border-right: 0;">개인정보 수집·이용 동의</td>
                    <td class="value" colspan="6" style="border-left: 0; border-right: 0;">□예</td>
                    <td class="value" colspan="6" style="border-left: 0;">□아니요</td>
                  </tr>
                </table>
                <div class="value" style="margin-top: 12mm;">${signatureYear}년&#160;&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;&#160;일</div>
                <div style="margin-top: 18mm; text-align: right;">
                  지원자 성명:${"&#160;".repeat(43)}( 서명 또는 인 )&#160;&#160;<br />
                  <br />
                  보호자 성명:${"&#160;".repeat(43)}( 서명 또는 인 )&#160;&#160;
                </div>
              </td>
            </tr>
          </table>
        </div>
    """.trimIndent()

    /**
     * 요강 <서식 6> 흡연 검사 동의서. 서식 5 와 뼈대가 같고 본문 문단과 개인정보 표의 수집목적만 다르다.
     *
     * 요강은 인적사항 표부터 아래까지를 상자 한 겹으로 두른다. 그래서 표 하나의 마지막 행을 본문 칸 하나로
     * 만들어 그 테두리가 곧 바깥 상자가 되게 했다. 본문 칸 200mm 는 상자가 요강만큼 내려오면서도
     * A4 한 장을 넘지 않는 높이다.
     *
     * 요강 6쪽 인적사항 칸에도 음영이 없어 [label] 대신 [value] 로 찍는다.
     *
     */
    private fun smokingTestConsentPage(signatureYear: Int, form: ApplicationForm) = """
        <div class="sheet">
          <p class="caption">&lt;서식 6&gt; (인터넷접수 후 출력)</p>
          <h1>흡연 검사 동의서</h1>
          <table>
            <colgroup>${"<col />".repeat(24)}</colgroup>
            <tr>
              <td class="value" rowspan="3" colspan="2">인적<br />사항</td>
              ${value("성 명", 4)}${value(form.name, 7)}
              ${value("접수번호", 4)}${value(form.applicantId.toString(), 7)}
            </tr>
            <tr>
              ${value("전화번호", 4)}${value(form.phoneNumber, 7)}
              ${value("출신학교", 4)}${value(form.school?.name, 7)}
            </tr>
            <tr>
              ${value("주 소", 4)}${leftValue(form.address, 18)}
            </tr>
            <tr>
              <td colspan="24" style="height: 200mm; padding: 6mm 5mm; vertical-align: top; text-align: left; font-size: 10pt; line-height: 1.9;">
                &#160;&#160;&#160;&#160;${SCHOOL_NAME}에서는 안전한 기숙사 생활과 학생들의 건강을 위해 신입생 입학
                전형자를 대상으로 소변 니코틴 검사 측정을 합니다. 이에 흡연검사에 <b><u>학생과 보호자의 동의</u></b>를 구합니다.<br />
                &#160;&#160;&#160;&#160;검사 시 약간의 불편이 있더라도 흡연자 지도를 위해서는 필요한 사안이므로 양해와
                적극적인 협조를 부탁드립니다.<br />
                &#160;&#160;&#160;&#160;학생들의 건강을 위해 흡연을 규제하고 예방하도록 학생 지도에 최선을 다하겠습니다.
                학부모님께서도 귀 자녀가 흡연의 위험성을 인지하고 흡연하지 않도록 지도와 협조를 부탁드립니다.<br />
                <br />
                <div class="sign">&#160;&#160;□ 개인정보 수집·이용 동의</div>
                <table style="width: 96%; margin: 2mm 0 2mm 2%;">
                  <tr>
                    <td class="label" style="width: 45%;">항 목</td>
                    <td class="label" style="width: 27.5%;">수집목적</td>
                    <td class="label" style="width: 27.5%;">보유기간</td>
                  </tr>
                  <tr>
                    <td class="value">학생(성명, 연락처,<br />출신 중학교, 주소)</td>
                    <td class="value">흡연검사 동의</td>
                    <td class="value" style="font-weight: bold;">3년</td>
                  </tr>
                </table>
                &#160;&#160;※ 개인정보 수집·이용에 대한 동의를 거부할 권리가 있습니다. 그러나 동의를 거부할 경우<br />
                &#160;&#160;&#160;&#160;최종 입학에 제한을 받을 수 있습니다.<br />
                <table style="width: 96%; margin: 6mm 0 0 2%;">
                  <tr>
                    <td class="left" colspan="12" style="border-right: 0;">개인정보 수집·이용 동의</td>
                    <td class="value" colspan="6" style="border-left: 0; border-right: 0;">□예</td>
                    <td class="value" colspan="6" style="border-left: 0;">□아니요</td>
                  </tr>
                </table>
                <div class="value" style="margin-top: 12mm;">${signatureYear}년&#160;&#160;&#160;&#160;&#160;월&#160;&#160;&#160;&#160;&#160;일</div>
                <div style="margin-top: 18mm; text-align: right;">
                  지원자 성명:${"&#160;".repeat(43)}( 서명 또는 인 )&#160;&#160;<br />
                  <br />
                  보호자 성명:${"&#160;".repeat(43)}( 서명 또는 인 )&#160;&#160;
                </div>
              </td>
            </tr>
          </table>
        </div>
    """.trimIndent()

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
