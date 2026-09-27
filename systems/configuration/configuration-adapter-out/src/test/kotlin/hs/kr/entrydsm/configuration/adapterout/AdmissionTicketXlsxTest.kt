package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicket
import hs.kr.entrydsm.configuration.domain.document.Applicant
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFPicture
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** 관리자 수험표 일괄 출력. 지난해 원서 시스템(Casper) 관리자 출력물 `수험표.xlsx` 의 칸 배치를 따른다. */
class AdmissionTicketXlsxTest {

    @Test
    fun `수험표를 20행 간격으로 이어 그리고 병합·행 높이·사진 자리는 지난해 양식과 같다`() {
        val xlsx = PoiAdmissionTicketSheetAdapter().render(
            listOf(
                ticket(12, "100001", AdmissionTicket.Photo("image/jpeg", jpeg())),
                // xlsx 에 넣을 수 없는 형식은 사진 칸을 비운다.
                ticket(13, examineeNumber = null, AdmissionTicket.Photo("image/webp", "RIFF0000WEBPVP8 ".toByteArray())),
            ),
        )

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/outputs.zip 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "admission-tickets.xlsx").writeBytes(xlsx) }

        XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
            val sheet = workbook.getSheet("수험표")
            fun cell(row: Int, column: Int) = sheet.getRow(row).getCell(column)

            // 지난해 양식 첫 장의 병합 그대로다. 둘째 장은 20행 아래에 같다.
            val first = listOf(
                "A2:F3", "A4:B15", "C4:D5", "E4:F5", "C6:D7", "E6:F7", "C8:D9", "E8:F9",
                "C10:D11", "E10:F11", "C12:D13", "E12:F13", "C14:D15", "E14:F15", "A16:F17",
            )
            val second = first.map { ref ->
                CellRangeAddress.valueOf(ref).apply {
                    firstRow += 20
                    lastRow += 20
                }.formatAsString()
            }
            assertEquals((first + second).sorted(), sheet.mergedRegions.map { it.formatAsString() }.sorted())
            assertEquals(13, sheet.defaultColumnWidth)
            assertEquals(listOf(48f, 16.5f, 16.5f, 48f), listOf(0, 1, 16, 20).map { sheet.getRow(it).heightInPoints })

            assertEquals("2027학년도 대덕소프트웨어마이스터고등학교 입학전형 수험표", cell(1, 0).stringCellValue)
            assertEquals(
                listOf(
                    "수험번호" to "100001", "성명" to "홍길동", "출신 중학교" to "대전한빛중학교",
                    "지역" to "대전", "전형 유형" to "일반전형", "접수 번호" to "0012",
                ),
                (0 until 6).map { cell(3 + it * 2, 2).stringCellValue to cell(3 + it * 2, 4).stringCellValue },
            )
            assertEquals("대덕소프트웨어마이스터고등학교장", cell(15, 0).stringCellValue)
            assertEquals("미발급", cell(23, 4).stringCellValue)
            assertEquals("0013", cell(33, 4).stringCellValue)

            // 사진은 첫 장의 A4:B15 를 채운다.
            val anchor = sheet.drawingPatriarch.shapes.filterIsInstance<XSSFPicture>().single().clientAnchor
            assertEquals(listOf(0, 3, 2, 15), listOf(anchor.col1.toInt(), anchor.row1, anchor.col2.toInt(), anchor.row2))

            val title = cell(1, 0).cellStyle
            assertTrue(title.font.bold)
            assertEquals(14.toShort(), title.font.fontHeightInPoints)
            assertEquals(listOf(BorderStyle.THIN, BorderStyle.THIN), listOf(title.borderTop, title.borderLeft))
            val label = cell(3, 2).cellStyle
            assertFalse(label.font.bold)
            assertEquals(11.toShort(), label.font.fontHeightInPoints)
            // 병합 칸의 오른쪽 아래 칸에도 테두리가 있다(사진 칸).
            val photoCorner = cell(14, 1).cellStyle
            assertEquals(listOf(BorderStyle.THIN, BorderStyle.THIN), listOf(photoCorner.borderBottom, photoCorner.borderRight))
        }
    }

    private fun ticket(applicantId: Long, examineeNumber: String?, photo: AdmissionTicket.Photo?) = AdmissionTicket.of(
        admissionYear = 2027,
        applicantId = applicantId,
        applicant = Applicant(10, "홍길동", "대전한빛중학교", Applicant.Region.DAEJEON, Applicant.AdmissionType.REGULAR, "photo_a"),
        examineeNumber = examineeNumber,
        photo = photo,
    )

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "jpg", it)
    }.toByteArray()
}
