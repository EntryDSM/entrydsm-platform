package hs.kr.entrydsm.configuration.domain.document

private const val SCHOOL_NAME = "대덕소프트웨어마이스터고등학교"
private const val PRINCIPAL_LINE = "대덕소프트웨어마이스터고등학교장"
private const val UNISSUED_EXAMINEE_NUMBER = "미발급"

/**
 * 수험표 레이아웃을 XHTML 로 만든다. PDF 변환기(openhtmltopdf)는 well-formed XHTML 만 받으므로 태그를 모두 닫는다.
 *
 * 개별 수험표(REST)와 관리자 일괄 출력(admin gRPC)이 이 양식 하나를 쓴다.
 *
 * ponytail: 수험번호는 admin 이 발급해 일괄 출력만 넘겨준다. 개별 수험표는 받을 길이 없어 미발급으로 찍는다.
 * 학생 화면에 수험표 출력이 붙으면 admin 에서 수험번호와 1차 합격 여부를 함께 받아 온다.
 */
object AdmissionTicketHtml {

    /** PDF 어댑터가 한글 폰트를 이 이름으로 등록한다. */
    const val FONT_FAMILY = "AdmissionTicket"

    fun render(
        admissionYear: Int,
        applicantId: Long,
        applicant: Applicant,
        examineeNumber: String?,
        photoDataUri: String?,
    ): String {
        val rows = listOf(
            "수험번호" to (examineeNumber ?: UNISSUED_EXAMINEE_NUMBER),
            "성명" to applicant.name,
            "출신 중학교" to applicant.schoolName,
            "지역" to applicant.region?.label,
            "전형 유형" to applicant.admissionType?.label,
            "접수 번호" to ReceiptNumber.of(applicantId),
        )

        return """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
              <title>수험표</title>
              <style>
                @page { size: A4; margin: 25mm 20mm; }
                body { font-family: "$FONT_FAMILY"; font-size: 11pt; }
                table { width: 100%; border-collapse: collapse; table-layout: fixed; }
                td { border: 1px solid #000000; padding: 6pt 8pt; }
                .title, .principal { text-align: center; font-weight: bold; font-size: 14pt; padding: 10pt 8pt; }
                .photo { width: 42%; background-color: #d9d9d9; vertical-align: bottom; text-align: left; }
                .photo img { display: block; width: 100%; }
                .label { width: 29%; height: 15mm; text-align: center; }
                .value { text-align: center; }
              </style>
            </head>
            <body>
              <table>
                <tr>
                  <td class="title" colspan="3">${escape("${admissionYear}학년도 $SCHOOL_NAME 입학전형 수험표")}</td>
                </tr>
                <tr>
                  <td class="photo" rowspan="${rows.size}">${photoCell(photoDataUri)}</td>
                  ${rows.first().let { (label, value) -> cells(label, value) }}
                </tr>
                ${rows.drop(1).joinToString("\n") { (label, value) -> "<tr>${cells(label, value)}</tr>" }}
                <tr>
                  <td class="principal" colspan="3">$PRINCIPAL_LINE</td>
                </tr>
              </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun cells(label: String, value: String?): String =
        """<td class="label">${escape(label)}</td><td class="value">${escape(value.orEmpty())}</td>"""

    private fun photoCell(photoDataUri: String?): String =
        photoDataUri?.let { """<img src="${escape(it)}" alt="사진" />""" } ?: "사진"

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
