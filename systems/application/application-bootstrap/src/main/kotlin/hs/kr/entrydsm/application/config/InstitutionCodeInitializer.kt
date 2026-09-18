package hs.kr.entrydsm.application.config

import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import hs.kr.entrydsm.application.adapterout.repository.InstitutionCodeJpaRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets

@Component
class InstitutionCodeInitializer(
    private val institutionCodeJpaRepository: InstitutionCodeJpaRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (institutionCodeJpaRepository.count() > 0) return

        val institutionCodes = ClassPathResource("institution-codes.csv")
            .inputStream
            .bufferedReader(StandardCharsets.UTF_8)
            .useLines { lines ->
                lines
                    .drop(1)
                    .filter(String::isNotBlank)
                    .map(::parseCsvLine)
                    .map(::toEntity)
                    .toList()
            }

        // 기관코드를 직접 PK로 사용하므로 saveAll 시 행마다 SELECT 후 INSERT가 발생할 수 있음.
        // 빈 DB 최초 기동 1회이므로 우선 유지하고, 필요하면 Persistable 적용.
        institutionCodeJpaRepository.saveAll(institutionCodes)
    }

    private fun toEntity(values: List<String>): InstitutionCodeJpaEntity {
        require(values.size == 8) {
            "institution code CSV row must have 8 columns: $values"
        }

        return InstitutionCodeJpaEntity(
            code = values[0],
            fullName = values[1],
            name = values[2],
            postalCode = values[3].ifBlank { null },
            status = values[4],
            address = values[5].ifBlank { null },
            phoneNumber = values[6].ifBlank { null },
            faxNumber = values[7].ifBlank { null },
        )
    }

    companion object {
        internal fun parseCsvLine(line: String): List<String> {
            val values = mutableListOf<String>()
            val value = StringBuilder()
            var quoted = false
            var index = 0

            while (index < line.length) {
                when {
                    line[index] == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                        value.append('"')
                        index++
                    }

                    line[index] == '"' -> quoted = !quoted

                    line[index] == ',' && !quoted -> {
                        values += value.toString()
                        value.clear()
                    }

                    else -> value.append(line[index])
                }

                index++
            }

            return values + value.toString()
        }
    }
}