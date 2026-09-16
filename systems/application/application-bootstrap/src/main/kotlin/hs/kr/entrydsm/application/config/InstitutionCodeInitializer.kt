package hs.kr.entrydsm.application.config

import java.nio.charset.Charset
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class InstitutionCodeInitializer(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM institution_codes LIMIT 1)", Boolean::class.java) == true) return

        val institutions = ClassPathResource("institution-codes.csv").inputStream.bufferedReader(CP949).useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map(::parseCsvLine).map(::InstitutionCode).toList()
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, institutions, 500) { statement, institution ->
            statement.setString(1, institution.code)
            statement.setString(2, institution.fullName)
            statement.setString(3, institution.name)
            statement.setString(4, institution.representativeCode)
            statement.setString(5, institution.type)
            statement.setString(6, institution.status)
            statement.setString(7, institution.registrant.takeIf(String::isNotBlank))
        }
    }

    internal fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when {
                line[index] == '"' && quoted && line.getOrNull(index + 1) == '"' -> value.append('"').also { index++ }
                line[index] == '"' -> quoted = !quoted
                line[index] == ',' && !quoted -> values += value.toString().also { value.clear() }
                else -> value.append(line[index])
            }
            index++
        }
        return values + value.toString()
    }

    private data class InstitutionCode(private val values: List<String>) {
        init {
            require(values.size == 7) { "institution code CSV row must have 7 columns" }
        }

        val code = values[0]
        val fullName = values[1]
        val name = values[2]
        val representativeCode = values[3]
        val type = values[4]
        val status = values[5]
        val registrant = values[6]
    }

    private companion object {
        val CP949: Charset = Charset.forName("MS949")
        const val INSERT_SQL = """
            INSERT INTO institution_codes
                (code, full_name, name, representative_code, type, status, registrant)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """
    }
}
