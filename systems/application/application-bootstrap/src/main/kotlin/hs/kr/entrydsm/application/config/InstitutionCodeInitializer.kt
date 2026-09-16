package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import hs.kr.entrydsm.application.adapterout.repository.InstitutionCodeJpaRepository
import java.nio.charset.Charset
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class InstitutionCodeInitializer(
    private val institutionCodeJpaRepository: InstitutionCodeJpaRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (institutionCodeJpaRepository.count() > 0) return

        val institutionCodes = ClassPathResource("institution-codes.csv").inputStream.bufferedReader(CP949).useLines { lines ->
            lines.drop(1).filter(String::isNotBlank).map(::parseCsvLine).map(::toEntity).toList()
        }
        // ponytail: 기관코드를 직접 넣는 PK라 saveAll 이 행마다 SELECT 후 INSERT 한다(빈 DB 첫 기동 1회). 느려지면 엔티티에 Persistable 을 붙인다.
        institutionCodeJpaRepository.saveAll(institutionCodes)
    }

    private fun toEntity(values: List<String>): InstitutionCodeJpaEntity {
        require(values.size == 7) { "institution code CSV row must have 7 columns" }
        return InstitutionCodeJpaEntity(
            code = values[0],
            fullName = values[1],
            name = values[2],
            representativeCode = values[3],
            type = values[4],
            status = values[5],
            registrant = values[6].takeIf(String::isNotBlank),
        )
    }

    companion object {
        private val CP949: Charset = Charset.forName("MS949")

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
    }
}
