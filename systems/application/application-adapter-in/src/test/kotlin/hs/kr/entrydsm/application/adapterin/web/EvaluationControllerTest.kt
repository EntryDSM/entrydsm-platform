package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveSubjectGradesRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SubjectGradesRequest
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluationControllerTest {
    @Test
    fun saveExpectedGradesConvertsSchoolSemester() {
        val evaluationPort = FakeEvaluationPort()
        val controller = EvaluationController(evaluationPort)

        controller.saveExpectedGrades(
            userId = 10L,
            request = SaveSubjectGradesRequest(
                schoolSemester = "3-1",
                subjects = subjectGradesRequest(),
            ),
        )

        assertEquals(
            SchoolSemester.THIRD_GRADE_FIRST_SEMESTER,
            evaluationPort.saveSubjectGradesCommand?.schoolSemester,
        )
        assertEquals(10L, evaluationPort.saveSubjectGradesCommand?.userId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveExpectedGradesRejectsInvalidSchoolSemester() {
        EvaluationController(FakeEvaluationPort()).saveExpectedGrades(
            userId = 10L,
            request = SaveSubjectGradesRequest(
                schoolSemester = "1-1",
                subjects = subjectGradesRequest(),
            ),
        )
    }

    @Test
    fun getResultCalculatesAndDoesNotExposeScores() {
        val evaluationPort = FakeEvaluationPort()
        val controller = EvaluationController(evaluationPort)

        val response = controller.getResult(
            userId = 10L,
        )

        assertEquals(null, response.data)
        assertEquals(10L, evaluationPort.calculateEvaluationCommand?.userId)
    }

    private class FakeEvaluationPort : EvaluationPort {
        var saveSubjectGradesCommand: SaveSubjectGradesCommand? = null
        var calculateEvaluationCommand: CalculateEvaluationCommand? = null

        override fun saveSubjectGrades(command: SaveSubjectGradesCommand) {
            saveSubjectGradesCommand = command
        }

        override fun saveGedScores(command: SaveGedScoresCommand) = Unit

        override fun saveAcademicRecord(command: SaveAcademicRecordCommand): AcademicRecordResult =
            AcademicRecordResult(
                absentCount = 0,
                earlyLeaveCount = 0,
                lateCount = 0,
                classAbsenceCount = 0,
                volunteerTime = 0,
            )

        override fun saveCertificates(command: SaveCertificatesCommand) = Unit

        override fun calculateResult(command: CalculateEvaluationCommand) {
            calculateEvaluationCommand = command
        }
    }

    private companion object {
        fun subjectGradesRequest(): SubjectGradesRequest =
            SubjectGradesRequest(
                koreanGrade = SubjectGrade.A,
                societyGrade = SubjectGrade.A,
                englishGrade = SubjectGrade.A,
                historyGrade = SubjectGrade.A,
                mathGrade = SubjectGrade.A,
                scienceGrade = SubjectGrade.A,
                technologyGrade = SubjectGrade.A,
            )
    }
}
