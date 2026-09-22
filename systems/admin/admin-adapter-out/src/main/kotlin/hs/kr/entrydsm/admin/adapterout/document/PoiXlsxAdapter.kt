package hs.kr.entrydsm.admin.adapterout.document

import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.io.ByteArrayOutputStream
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFCellStyle
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

    override fun renderApplicationChecklist(rows: List<FirstPassRow>): ByteArray =
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("지원자 점검표")
            val styles = ChecklistStyles(workbook)
            (1..7).forEach { sheet.setColumnWidth(it, 14 * 256) }

            rows.forEachIndexed { index, applicant ->
                val base = index * 20
                sheet.createRow(base).heightInPoints = 71f
                fun cell(row: Int, column: Int, value: Any?, style: XSSFCellStyle = styles.value) {
                    val target = sheet.getRow(base + row) ?: sheet.createRow(base + row)
                    val created = target.createCell(column)
                    created.cellStyle = style
                    created.set(value)
                }
                fun merge(row: Int, first: Int, last: Int) {
                    sheet.addMergedRegion(CellRangeAddress(base + row, base + row, first, last))
                    val mergeStyle = sheet.getRow(base + row).getCell(first).cellStyle
                    (first..last).forEach { column ->
                        val target = sheet.getRow(base + row) ?: sheet.createRow(base + row)
                        (target.getCell(column) ?: target.createCell(column)).cellStyle = mergeStyle
                    }
                }

                cell(1, 1, "접수번호", styles.label)
                cell(1, 2, applicant.receiptNumber.toLongOrNull() ?: applicant.receiptNumber, styles.receipt)
                cell(1, 3, applicant.schoolName ?: "X", styles.school)
                merge(1, 3, 5)
                cell(1, 6, applicant.graduationStatus.checklistGraduation(), styles.label)
                cell(1, 7, applicant.graduationYear, styles.value)

                cell(3, 1, applicant.region, styles.region)
                cell(3, 2, applicant.name, styles.name)
                merge(3, 2, 3)
                cell(3, 5, "학번", styles.subLabel)
                cell(3, 6, applicant.studentNumber ?: "X", styles.value)
                cell(3, 7, null)

                cell(4, 1, applicant.admissionType.checklistAdmission(), styles.admission)
                cell(4, 2, applicant.birthDate, styles.value)
                merge(4, 2, 3)
                cell(4, 5, "학생", styles.subLabel)
                cell(4, 6, applicant.phoneNumber, styles.value)
                merge(4, 6, 7)

                cell(5, 1, applicant.specialAdmissionType.checklistSpecial(), styles.special)
                cell(5, 2, applicant.gender, styles.value)
                merge(5, 2, 3)
                cell(5, 5, "보호자", styles.subLabel)
                cell(5, 6, applicant.guardianPhoneNumber, styles.value)
                merge(5, 6, 7)

                listOf("결석", "지각", "조퇴", "결과", "출석점수", "봉사시간", "봉사점수")
                    .forEachIndexed { column, value -> cell(7, column + 1, value, styles.label) }
                listOf(
                    applicant.absentCount,
                    applicant.lateCount,
                    applicant.earlyLeaveCount,
                    applicant.classAbsenceCount,
                    applicant.attendanceScore,
                    applicant.volunteerTime,
                    applicant.volunteerScore,
                ).forEachIndexed { column, value -> cell(8, column + 1, value) }

                listOf("과목", "3_2학기", "3_1학기", "직전", "직전전")
                    .forEachIndexed { column, value -> cell(10, column + 1, value, styles.label) }
                cell(10, 6, "교과성적", styles.subLabel)
                cell(10, 7, applicant.gradeTotal())

                val subjects = listOf("국어", "사회", "역사", "수학", "과학", "기술가정", "영어")
                val semesters = listOf(
                    applicant.thirdGradeSecondSemester,
                    applicant.thirdGradeFirstSemester,
                    applicant.previousSemester,
                    applicant.secondPreviousSemester,
                )
                subjects.forEachIndexed { subjectIndex, subject ->
                    cell(11 + subjectIndex, 1, subject, styles.subject)
                    semesters.forEachIndexed { semesterIndex, grades ->
                        cell(11 + subjectIndex, 2 + semesterIndex, grades.values()[subjectIndex])
                    }
                }
                cell(11, 6, "대회", styles.subLabel)
                cell(11, 7, applicant.awarded.mark())
                cell(12, 6, "기능사", styles.subLabel)
                cell(12, 7, applicant.certified.mark())
                cell(13, 6, "가산점", styles.subLabel)
                cell(13, 7, applicant.additionalScore)

                cell(18, 1, "점수", styles.label)
                listOf(
                    applicant.thirdGradeSecondSemester.scoreTotal(),
                    applicant.thirdGradeFirstSemester.scoreTotal(),
                    applicant.previousSemester.scoreTotal(),
                    applicant.secondPreviousSemester.scoreTotal(),
                ).forEachIndexed { column, value -> cell(18, column + 2, value) }
                cell(18, 6, "환산점수", styles.subLabel)
                cell(18, 7, applicant.subjectScore)
                cell(19, 6, "총점", styles.subLabel)
                cell(19, 7, applicant.totalScore)
            }

            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

    private fun Cell.set(value: Any?) = when (value) {
        null -> Unit
        is Number -> setCellValue(value.toDouble())
        else -> setCellValue(value.toString())
    }

    private fun String?.checklistGraduation() = when (this) {
        "졸업예정" -> "졸업예정자"
        else -> this
    }

    private fun String?.checklistAdmission() = when (this) {
        "마이스터전형" -> "마이스터인재"
        "사회통합전형" -> "사회특별전형"
        else -> this
    }

    private fun String?.checklistSpecial() = when (this) {
        "해당없음" -> "없음"
        "특례입학" -> "특례입학대상"
        else -> this
    }

    private fun Boolean?.mark() = when (this) {
        true -> "O"
        false -> "X"
        null -> null
    }

    private fun FirstPassRow.gradeTotal(): Double? =
        listOf(
            thirdGradeSecondSemester.scoreTotal(),
            thirdGradeFirstSemester.scoreTotal(),
            previousSemester.scoreTotal(),
            secondPreviousSemester.scoreTotal(),
        )
            .filterNotNull()
            .takeIf { it.isNotEmpty() }
            ?.sum()

    private fun hs.kr.entrydsm.admin.domain.model.SemesterGrades.values() =
        listOf(korean, society, history, math, science, technology, english)

    private fun hs.kr.entrydsm.admin.domain.model.SemesterGrades.scoreTotal(): Double? =
        total ?: 0.0.takeIf { values().any { value -> value != null } }

    private class ChecklistStyles(private val workbook: XSSFWorkbook) {
        private fun style(color: IndexedColors? = null, bold: Boolean = false, size: Short = 10): XSSFCellStyle =
            workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                borderTop = BorderStyle.THIN
                borderBottom = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
                if (color != null) {
                    fillForegroundColor = color.index
                    fillPattern = FillPatternType.SOLID_FOREGROUND
                }
                setFont(workbook.createFont().apply {
                    this.bold = bold
                    fontHeightInPoints = size
                })
            }

        val value = style()
        val label = style(IndexedColors.LIGHT_CORNFLOWER_BLUE, bold = true)
        val subLabel = style(IndexedColors.LIGHT_YELLOW, bold = true)
        val receipt = style(IndexedColors.LIGHT_GREEN, bold = true, size = 14)
        val school = style(bold = true, size = 14)
        val name = style(IndexedColors.LIGHT_GREEN, bold = true, size = 14)
        val region = style(IndexedColors.LIGHT_GREEN, bold = true)
        val admission = style(IndexedColors.LIGHT_YELLOW, bold = true)
        val special = style(IndexedColors.LIGHT_ORANGE, bold = true)
        val subject = style(IndexedColors.LIGHT_CORNFLOWER_BLUE, bold = true)
    }
}
