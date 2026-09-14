package hs.kr.entrydsm.admin.adapterout.document

import java.io.ByteArrayInputStream
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoiXlsxAdapterTest {

    @Test
    fun `숫자는 숫자 칸, null 은 빈 칸, 수식처럼 보이는 글자는 글자 칸으로 쓴다`() {
        val xlsx = PoiXlsxAdapter().render(
            sheetName = "지원자 목록",
            header = listOf("접수번호", "성명", "총점"),
            rows = listOf(listOf(7, "=HYPERLINK(\"http://x\")", null)),
        )

        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
            val sheet = workbook.getSheet("지원자 목록")
            assertEquals("접수번호", sheet.getRow(0).getCell(0).stringCellValue)

            val row = sheet.getRow(1)
            assertEquals(CellType.NUMERIC, row.getCell(0).cellType)
            assertEquals(7.0, row.getCell(0).numericCellValue, 0.0)
            assertEquals(CellType.STRING, row.getCell(1).cellType)
            assertEquals("=HYPERLINK(\"http://x\")", row.getCell(1).stringCellValue)
            assertNull(row.getCell(2))
        }
    }
}
