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

    @Test
    fun gedPersistenceKeepsNullAndActualZeroDistinct() {
        val entity = AcademicRecordJpaEntity()
        entity.updateFrom(AcademicRecord(gedScores = GedScores(100, 100, 100, 100, 100)))
        assertNull(entity.gedScores?.technologyScore)
        assertNull(entity.toDomain().gedScores?.technologyScore)
        entity.updateFrom(AcademicRecord(gedScores = GedScores(100, 100, 100, 100, 100, 0)))
        assertEquals(0, entity.gedScores?.technologyScore)
        assertEquals(0, entity.toDomain().gedScores?.technologyScore)
    }

    @Test
    fun gedMigrationPreservesActualZeroAllowsNullAndDropsHistory() {
        val sql = requireNotNull(javaClass.getResourceAsStream("/db/migration/V019__make_ged_technology_optional_and_remove_history.sql")).bufferedReader().use { it.readText() }
        java.sql.DriverManager.getConnection("jdbc:h2:mem:ged432;MODE=MySQL;DB_CLOSE_DELAY=-1").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE ged_scores (id BIGINT PRIMARY KEY, technology_score INT NOT NULL DEFAULT 0, history_score INT NOT NULL)")
                statement.execute("INSERT INTO ged_scores VALUES (1, 0, 95)")
                sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach { statement.execute(it) }
                statement.executeQuery("SELECT technology_score FROM ged_scores WHERE id=1").use { result ->
                    result.next(); assertEquals(0, result.getInt(1)); org.junit.Assert.assertFalse(result.wasNull())
                }
                statement.execute("INSERT INTO ged_scores(id,technology_score) VALUES(2,NULL)")
                statement.executeQuery("SELECT technology_score FROM ged_scores WHERE id=2").use { result ->
                    result.next(); assertNull(result.getObject(1))
                }
                connection.metaData.getColumns(null, null, "GED_SCORES", "HISTORY_SCORE").use { columns ->
                    org.junit.Assert.assertFalse(columns.next())
                }
            }
        }
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

        )
}
