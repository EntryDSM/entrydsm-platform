package hs.kr.entrydsm.admin.adapterout.document

import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Component

/**
 * Apache POI 로 xlsx 를 만듭니다.
 *
 * 글자는 글자 칸으로만 쓰므로 `=` 로 시작하는 값도 수식으로 실행되지 않습니다.
 * CSV 처럼 선두 문자를 무력화할 필요가 없습니다.
 */
@Component
class PoiXlsxAdapter : XlsxRenderPort {

    override fun render(sheetName: String, header: List<String>, rows: List<List<Any?>>): ByteArray =
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(sheetName)
            (listOf(header) + rows).forEachIndexed { rowIndex, values ->
                val row = sheet.createRow(rowIndex)
                values.forEachIndexed { columnIndex, value ->
                    when (value) {
                        null -> Unit
                        is Number -> row.createCell(columnIndex).setCellValue(value.toDouble())
                        else -> row.createCell(columnIndex).setCellValue(value.toString())
                    }
                }
            }
            sheet.createFreezePane(0, 1)
            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }
}
