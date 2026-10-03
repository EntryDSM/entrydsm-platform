package hs.kr.entrydsm.application

import hs.kr.entrydsm.application.config.InstitutionCodeInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.sql.DriverManager

class ApplicationBootstrapApplicationTest {
    @Test
    fun restoresBackfilledScoresWithoutChangingCalculatedScores() {
        DriverManager.getConnection("jdbc:h2:mem:score_migration;MODE=MySQL").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE applicants (id INT PRIMARY KEY, total_score DOUBLE NULL, total_score_updated_at TIMESTAMP NULL)")
                statement.execute("INSERT INTO applicants VALUES (1, NULL, NULL), (2, 0, TIMESTAMP '2026-10-01 00:00:00'), (3, 150, NULL), (4, 0, NULL)")
                listOf("V012__make_applicant_total_score_not_null.sql", "V013__restore_nullable_applicant_total_score.sql").forEach { migration ->
                    val sql = requireNotNull(javaClass.getResourceAsStream("/db/migration/$migration"))
                        .bufferedReader().use { it.readText() }
                    sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach(statement::execute)
                }
                statement.execute("INSERT INTO applicants (id) VALUES (5)")
                statement.executeQuery("SELECT total_score FROM applicants ORDER BY id").use { rows ->
                    val expected = listOf(null, 0.0, 150.0, null, null)
                    expected.forEach { score ->
                        assertTrue(rows.next())
                        if (score == null) assertNull(rows.getObject(1)) else assertEquals(score, rows.getDouble(1), 0.0)
                    }
                    assertTrue(!rows.next())
                }
            }
        }
    }

    @Test
    fun contextLoads() {
        assertTrue(true)
    }

    @Test
    fun parsesQuotedInstitutionCodeCsvValues() {
        assertEquals(
            listOf("7010977", "서울특별시교육청, 국악학교", "국악\"학교", "7010000", "중학교", "현존", ""),
            InstitutionCodeInitializer.parseCsvLine("7010977,\"서울특별시교육청, 국악학교\",\"국악\"\"학교\",7010000,중학교,현존,"),
        )
    }
}
