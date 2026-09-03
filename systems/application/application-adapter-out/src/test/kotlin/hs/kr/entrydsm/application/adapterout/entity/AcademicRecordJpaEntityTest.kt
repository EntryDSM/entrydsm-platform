package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AcademicRecordJpaEntityTest {
    @Test
    fun updateFromUpdatesSubjectGradeEntityInPlace() {
        val entity = AcademicRecordJpaEntity()
        entity.updateFrom(
            AcademicRecord(
                subjectGrades = linkedMapOf(
                    SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to subjectGrades(SubjectGrade.B),
                ),
            ),
        )
        val savedGrade = entity.subjectGrades.single()

        entity.updateFrom(
            AcademicRecord(
                subjectGrades = linkedMapOf(
                    SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to subjectGrades(SubjectGrade.A),
                ),
            ),
        )

        assertSame(savedGrade, entity.subjectGrades.single())
        assertEquals(SubjectGrade.A, entity.subjectGrades.single().koreanGrade)
        assertSame(entity, entity.subjectGrades.single().academicRecord)
    }

    @Test
    fun updateFromRemovesStaleSubjectGrades() {
        val entity = AcademicRecordJpaEntity()
        entity.updateFrom(
            AcademicRecord(
                subjectGrades = linkedMapOf(
                    SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to subjectGrades(SubjectGrade.A),
                    SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to subjectGrades(SubjectGrade.B),
                ),
            ),
        )

        entity.updateFrom(
            AcademicRecord(
                subjectGrades = linkedMapOf(
                    SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to subjectGrades(SubjectGrade.C),
                ),
            ),
        )

        assertEquals(
            listOf(SchoolSemester.SECOND_GRADE_SECOND_SEMESTER),
            entity.subjectGrades.map { it.id.schoolSemester },
        )
    }

    @Test
    fun updateFromUpdatesGedScoreEntityInPlace() {
        val entity = AcademicRecordJpaEntity()
        entity.updateFrom(AcademicRecord(gedScores = gedScores(80)))
        val savedGedScores = entity.gedScores

        entity.updateFrom(AcademicRecord(gedScores = gedScores(90)))

        assertSame(savedGedScores, entity.gedScores)
        assertEquals(90, entity.gedScores?.koreanScore)
        assertSame(entity, entity.gedScores?.academicRecord)
    }

    @Test
    fun updateFromRemovesGedScoresWhenDomainDoesNotHaveGedScores() {
        val entity = AcademicRecordJpaEntity()
        entity.updateFrom(AcademicRecord(gedScores = gedScores(80)))

        entity.updateFrom(AcademicRecord())

        assertNull(entity.gedScores)
    }

    private fun subjectGrades(grade: SubjectGrade): SubjectGrades =
        SubjectGrades(
            koreanGrade = grade,
            mathGrade = grade,
            englishGrade = grade,
            scienceGrade = grade,
            societyGrade = grade,
            technologyGrade = grade,
            historyGrade = grade,
        )

    private fun gedScores(score: Int): GedScores =
        GedScores(
            koreanScore = score,
            mathScore = score,
            englishScore = score,
            scienceScore = score,
            societyScore = score,
            technologyScore = score,
            historyScore = score,
        )
}
