package hs.kr.entrydsm.application.config

import java.math.BigDecimal
import java.sql.Connection
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component

@Component
@DependsOnDatabaseInitialization
class PostalCodeLocationInitializer(
    private val dataSource: DataSource,
    @Value("classpath:postal-code-locations.csv")
    private val csvResource: Resource = ClassPathResource("postal-code-locations.csv"),
) : ApplicationRunner {
    /** 초기화 잠금을 획득한 뒤 테이블이 완전히 비어 있을 때만 전체 좌표를 적재한다. */
    override fun run(args: ApplicationArguments) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement("SELECT id FROM postal_code_location_seed_lock WHERE id = 1 FOR UPDATE").use { statement ->
                    statement.executeQuery().use { rows ->
                        check(rows.next()) { "우편번호 초기화 잠금 행이 없습니다." }
                    }
                }
                val populated = connection.prepareStatement("SELECT 1 FROM postal_code_locations LIMIT 1").use { statement ->
                    statement.executeQuery().use { it.next() }
                }
                if (!populated) insertLocations(connection)
                connection.commit()
            } catch (error: Throwable) {
                try {
                    connection.rollback()
                } catch (rollbackError: Throwable) {
                    error.addSuppressed(rollbackError)
                }
                throw error
            }
        }
    }

    /** 검증된 CSV를 배치로 넣고 시각은 UTC로 통일한다. */
    private fun insertLocations(connection: Connection) {
        connection.prepareStatement(
            "INSERT INTO postal_code_locations (zip_code, representative_address, latitude, longitude, updated_at) VALUES (?, ?, ?, ?, ?)",
        ).use { statement ->
            csvResource.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val header = reader.readLine()?.removePrefix("\uFEFF")
                require(header == "zip_code,representative_address,latitude,longitude,updated_at") {
                    "우편번호 CSV 헤더가 올바르지 않습니다."
                }
                var count = 0
                reader.lineSequence().filter(String::isNotBlank).forEach { line ->
                    val values = InstitutionCodeInitializer.parseCsvLine(line)
                    require(values.size == 5) { "우편번호 CSV는 5개 열이어야 합니다." }
                    require(values[0].matches(Regex("[0-9]{5}"))) { "우편번호는 5자리 숫자여야 합니다." }
                    require(values[1].isNotBlank() && values[1].length <= 255) { "대표 주소가 올바르지 않습니다." }
                    val latitude = values[2].toBigDecimal()
                    val longitude = values[3].toBigDecimal()
                    require(latitude in BigDecimal("32")..BigDecimal("40") && longitude in BigDecimal("123")..BigDecimal("133")) {
                        "우편번호 좌표가 국내 범위를 벗어났습니다."
                    }
                    val updatedAt = OffsetDateTime.parse(values[4]).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
                    statement.setString(1, values[0])
                    statement.setString(2, values[1])
                    statement.setBigDecimal(3, latitude)
                    statement.setBigDecimal(4, longitude)
                    statement.setObject(5, updatedAt)
                    statement.addBatch()
                    count++
                    if (count % 1000 == 0) statement.executeBatch()
                }
                require(count > 0) { "우편번호 CSV 데이터가 없습니다." }
                if (count % 1000 != 0) statement.executeBatch()
            }
        }
    }
}
