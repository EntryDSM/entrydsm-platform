package hs.kr.entrydsm.admin.adapterout.document

import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.model.SemesterGrades
import hs.kr.entrydsm.admin.domain.port.out.XlsxRenderPort
import java.io.ByteArrayOutputStream
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellCopyPolicy
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

    override fun renderApplicationChecklist(rows: List<FirstPassRow>): ByteArray =
        XSSFWorkbook(requireNotNull(javaClass.getResourceAsStream("/forms/지원자 점검표.xlsx"))).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            workbook.setSheetName(0, "지원자 점검표")

            rows.forEachIndexed { index, applicant ->
                val base = index * 20
                if (base > sheet.lastRowNum) {
                    sheet.copyRows(0, 19, base, CellCopyPolicy())
                }
                fun cell(row: Int, column: Int, value: Any?) {
                    val target = sheet.getRow(base + row) ?: sheet.createRow(base + row)
                    (target.getCell(column) ?: target.createCell(column)).set(value)
                }

                cell(1, 2, applicant.receiptNumber.toLongOrNull() ?: applicant.receiptNumber)
                cell(1, 3, applicant.schoolName ?: "X")
                cell(1, 6, applicant.graduationStatus.checklistGraduation())
                cell(1, 7, applicant.graduationYear)

                cell(3, 1, applicant.region)
                cell(3, 2, applicant.name)
                cell(3, 6, applicant.studentNumber ?: "X")
                cell(3, 7, null)

                cell(4, 1, applicant.admissionType.checklistAdmission())
                cell(4, 2, applicant.birthDate)
                cell(4, 6, applicant.phoneNumber)

                cell(5, 1, applicant.specialAdmissionType.checklistSpecial())
                cell(5, 2, applicant.gender)
                cell(5, 6, applicant.guardianPhoneNumber)

                listOf(
                    applicant.absentCount,
                    applicant.lateCount,
                    applicant.earlyLeaveCount,
                    applicant.classAbsenceCount,
                    applicant.attendanceScore,
                    applicant.volunteerTime,
                    applicant.volunteerScore,
                ).forEachIndexed { column, value -> cell(8, column + 1, value) }

                cell(10, 7, applicant.gradeTotal())

                val semesters = listOf(
                    applicant.thirdGradeSecondSemester,
                    applicant.thirdGradeFirstSemester,
                    applicant.previousSemester,
                    applicant.secondPreviousSemester,
                )
                repeat(7) { subjectIndex ->
                    semesters.forEachIndexed { semesterIndex, grades ->
                        cell(11 + subjectIndex, 2 + semesterIndex, grades.values()[subjectIndex])
                    }
                }
                cell(11, 7, applicant.awarded.mark())
                cell(12, 7, applicant.certified.mark())
                cell(13, 7, applicant.additionalScore)

                listOf(
                    applicant.thirdGradeSecondSemester.scoreTotal(),
                    applicant.thirdGradeFirstSemester.scoreTotal(),
                    applicant.previousSemester.scoreTotal(),
                    applicant.secondPreviousSemester.scoreTotal(),
                ).forEachIndexed { column, value -> cell(18, column + 2, value) }
                cell(18, 7, applicant.subjectScore)
                cell(19, 7, applicant.totalScore)
            }

            if (sheet.lastRowNum >= rows.size * 20) {
                (sheet.lastRowNum downTo rows.size * 20).forEach { sheet.getRow(it)?.let(sheet::removeRow) }
            }
            (sheet.numMergedRegions - 1 downTo 0).forEach { index ->
                if (sheet.getMergedRegion(index).firstRow >= rows.size * 20) sheet.removeMergedRegion(index)
            }

            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }

    private fun Cell.set(value: Any?) = when (value) {
        null -> setBlank()
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
        listOfNotNull(
            thirdGradeSecondSemester.scoreTotal(),
            thirdGradeFirstSemester.scoreTotal(),
            previousSemester.scoreTotal(),
            secondPreviousSemester.scoreTotal(),
        )
            .takeIf { it.isNotEmpty() }
            ?.sum()

    private fun SemesterGrades.values() =
        listOf(korean, society, history, math, science, technology, english)

    private fun SemesterGrades.scoreTotal(): Double? =
        total ?: 0.0.takeIf { values().any { value -> value != null } }

}
