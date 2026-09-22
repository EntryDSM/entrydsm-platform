package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicket
import hs.kr.entrydsm.configuration.domain.document.port.out.AdmissionTicketSheetPort
import org.apache.poi.poifs.filesystem.FileMagic
import org.apache.poi.ss.usermodel.BorderExtent
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.PropertyTemplate
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

private const val SHEET_NAME = "수험표"
private const val FONT_NAME = "맑은 고딕"
private const val COLUMN_WIDTH = 13

/** 수험표 한 장이 차지하는 행. 17행을 그리고 3행을 띄운다. */
private const val TICKET_ROWS = 20

/**
 * 수험표를 xlsx 한 시트에 20행 간격으로 이어 그린다. 지난해 원서 시스템(Casper-Application) 관리자 출력물
 * `수험표.xlsx` 와 같은 양식이다. A~F 열은 기본 폭(13)이고, 한 장은 위에서부터
 *
 * - 1행: 띄움 (48pt)
 * - 2~3행: 제목 (A:F)
 * - 4~15행: 증명사진 (A:B) | 이름표 (C:D) | 값 (E:F). 이름표·값은 두 행씩 여섯 칸
 * - 16~17행: 학교장 (A:F)
 *
 * 이고, 병합한 칸마다 가는 테두리를 두른다. 사진은 A:B 칸을 꽉 채우게 늘린다.
 */
@Component
class PoiAdmissionTicketSheetAdapter : AdmissionTicketSheetPort {

    override fun render(tickets: List<AdmissionTicket>): ByteArray =
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(SHEET_NAME)
            sheet.defaultColumnWidth = COLUMN_WIDTH
            val heading = workbook.style(bold = true, points = 14)
            val text = workbook.style(bold = false, points = 11)
            val borders = PropertyTemplate()
            tickets.forEachIndexed { index, ticket -> sheet.drawTicket(index * TICKET_ROWS, ticket, heading, text, borders) }
            // 칸의 글꼴·정렬은 그대로 두고 테두리만 더한다. 병합 칸의 가장자리 칸도 이때 만든다.
            borders.applyBorders(sheet)
            ByteArrayOutputStream().also(workbook::write).toByteArray()
        }

    private fun XSSFSheet.drawTicket(
        top: Int,
        ticket: AdmissionTicket,
        heading: XSSFCellStyle,
        text: XSSFCellStyle,
        borders: PropertyTemplate,
    ) {
        val bodyTop = top + 3
        val bodyBottom = bodyTop + ticket.rows.size * 2 - 1
        createRow(top).heightInPoints = 48f
        (top + 1..bodyBottom + 2).forEach { createRow(it).heightInPoints = 16.5f }

        fun box(firstRow: Int, lastRow: Int, firstColumn: Int, lastColumn: Int, style: XSSFCellStyle, value: String? = null) {
            val region = CellRangeAddress(firstRow, lastRow, firstColumn, lastColumn)
            addMergedRegion(region)
            borders.drawBorders(region, BorderStyle.THIN, BorderExtent.OUTSIDE)
            getRow(firstRow).createCell(firstColumn).apply {
                cellStyle = style
                value?.let(::setCellValue)
            }
        }

        box(top + 1, top + 2, 0, 5, heading, ticket.title)
        box(bodyTop, bodyBottom, 0, 1, text)
        ticket.rows.forEachIndexed { index, (label, value) ->
            val row = bodyTop + index * 2
            box(row, row + 1, 2, 3, text, label)
            box(row, row + 1, 4, 5, text, value)
        }
        box(bodyBottom + 1, bodyBottom + 2, 0, 5, heading, AdmissionTicket.PRINCIPAL_LINE)
        ticket.photo?.let { drawPhoto(it.bytes, bodyTop, bodyBottom) }
    }

    /**
     * 형식은 올린 파일 확장자가 아니라 내용으로 가린다.
     *
     * ponytail: xlsx 에 넣을 수 없는 형식(webp 등)은 사진 칸을 비운다. 프론트는 JPEG·PNG 만 올리게 한다.
     */
    private fun XSSFSheet.drawPhoto(bytes: ByteArray, firstRow: Int, lastRow: Int) {
        val type = when (FileMagic.valueOf(bytes)) {
            FileMagic.JPEG -> Workbook.PICTURE_TYPE_JPEG
            FileMagic.PNG -> Workbook.PICTURE_TYPE_PNG
            else -> return
        }
        // 시트에 그림판이 있으면 그것을 돌려준다.
        createDrawingPatriarch().createPicture(
            XSSFClientAnchor(0, 0, 0, 0, 0, firstRow, 2, lastRow + 1),
            workbook.addPicture(bytes, type),
        )
    }

    private fun XSSFWorkbook.style(bold: Boolean, points: Short): XSSFCellStyle = createCellStyle().apply {
        setFont(
            createFont().apply {
                this.bold = bold
                fontHeightInPoints = points
                fontName = FONT_NAME
            },
        )
        alignment = HorizontalAlignment.CENTER
        verticalAlignment = VerticalAlignment.CENTER
        // 긴 학교 이름이 두 행 높이 칸 안에서 줄을 바꾼다.
        wrapText = true
    }
}
