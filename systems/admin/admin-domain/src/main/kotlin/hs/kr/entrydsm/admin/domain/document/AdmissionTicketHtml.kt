package hs.kr.entrydsm.admin.domain.document

import hs.kr.entrydsm.admin.domain.model.AdmissionTicket

private const val SCHOOL_NAME = "대덕소프트웨어마이스터고등학교"
private const val PRINCIPAL_LINE = "대덕소프트웨어마이스터고등학교장"
private const val UNISSUED_EXAMINEE_NUMBER = "미발급"

/**
 * 수험표 레이아웃을 XHTML 문자열로 만듭니다.
 *
 * PDF 변환기(openhtmltopdf)는 well-formed XHTML만 받으므로 태그를 모두 닫아야 합니다.
 * 렌더링 규칙을 도메인에 두어 PDF 라이브러리 없이도 레이아웃을 검증할 수 있게 했습니다.
 */
object AdmissionTicketHtml {

    fun render(ticket: AdmissionTicket): String {
        val rows = listOf(
            "수험번호" to (ticket.examineeNumber ?: UNISSUED_EXAMINEE_NUMBER),
            "성명" to ticket.name,
            "출신 중학교" to ticket.schoolName,
            "지역" to ticket.region.label,
            "전형 유형" to ticket.admissionType.label,
            "접수 번호" to ticket.receiptNumber.toString(),
        )

        return """
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
              <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
              <title>수험표</title>
              <style>
                @page { size: A4; margin: 25mm 20mm; }
                body { font-family: "AdmissionTicket"; font-size: 11pt; }
                table { width: 100%; border-collapse: collapse; table-layout: fixed; }
                td { border: 1px solid #000000; padding: 6pt 8pt; }
                .title, .principal { text-align: center; font-weight: bold; font-size: 14pt; padding: 10pt 8pt; }
                .photo { width: 30%; background-color: #d9d9d9; vertical-align: bottom; text-align: left; }
                .photo img { display: block; width: 100%; }
                .label { width: 30%; text-align: center; }
                .value { text-align: center; }
              </style>
            </head>
            <body>
              <table>
                <tr>
                  <td class="title" colspan="3">${escape("${ticket.admissionYear}학년도 $SCHOOL_NAME 입학전형 수험표")}</td>
                </tr>
                <tr>
                  <td class="photo" rowspan="6">${photoCell(ticket.photoDataUri)}</td>
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

    private fun cells(label: String, value: String): String =
        """<td class="label">${escape(label)}</td><td class="value">${escape(value)}</td>"""

    private fun photoCell(photoDataUri: String?): String =
        photoDataUri?.let { """<img src="${escape(it)}" alt="사진" />""" } ?: "사진"

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
