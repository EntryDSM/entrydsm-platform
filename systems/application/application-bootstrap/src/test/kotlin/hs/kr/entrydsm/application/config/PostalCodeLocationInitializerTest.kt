package hs.kr.entrydsm.application.config

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

class PostalCodeLocationInitializerTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcTemplate
    private val header = "zip_code,representative_address,latitude,longitude,updated_at\n"
    private val first = "01234,서울특별시 강북구 가길 1,37.6300000,127.0200000,2026-10-18T08:00:00+09:00\n"

    @Before fun schema() {
        dataSource = DriverManagerDataSource("jdbc:h2:mem:postal_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        jdbc = JdbcTemplate(dataSource)
        ResourceDatabasePopulator(ClassPathResource("db/migration/V021__create_postal_code_locations.sql")).execute(dataSource)
    }

    @Test fun `빈 테이블에 우편번호와 좌표 및 UTC 시각을 보존한다`() {
        initialize(csv(first + "44663,울산광역시 남구 가길 2,35.5300000,129.2900000,2026-10-18T00:00:00Z\n"))
        assertEquals(2L, count())
        assertEquals("서울특별시 강북구 가길 1", jdbc.queryForObject("SELECT representative_address FROM postal_code_locations WHERE zip_code='01234'", String::class.java))
        assertEquals(0, BigDecimal("37.6300000").compareTo(jdbc.queryForObject("SELECT latitude FROM postal_code_locations WHERE zip_code='01234'", BigDecimal::class.java)))
        assertEquals(0, BigDecimal("127.0200000").compareTo(jdbc.queryForObject("SELECT longitude FROM postal_code_locations WHERE zip_code='01234'", BigDecimal::class.java)))
        assertEquals(LocalDateTime.of(2026, 10, 17, 23, 0), jdbc.queryForObject("SELECT updated_at FROM postal_code_locations WHERE zip_code='01234'", LocalDateTime::class.java))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM postal_code_locations WHERE zip_code='44663'", Long::class.java))
    }

    @Test fun `한 건이라도 있으면 CSV를 열지 않고 기존 값을 보존한다`() {
        initialize(csv(first))
        val before = jdbc.queryForList("SELECT * FROM postal_code_locations")
        val unreadable = object : ByteArrayResource(byteArrayOf()) {
            override fun getInputStream(): java.io.InputStream = throw AssertionError("기존 데이터가 있으면 CSV 접근 금지")
        }
        initialize(unreadable)
        assertEquals(before, jdbc.queryForList("SELECT * FROM postal_code_locations"))
    }

    @Test fun `실제 리소스 전체를 적재하고 재시작해도 변경하지 않는다`() {
        val startupUrl = "jdbc:h2:mem:postal_startup_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1"
        SpringApplicationBuilder(StartupConfig::class.java).web(WebApplicationType.NONE).properties(
            "spring.config.location=optional:classpath:postal-initializer-empty.yml",
            "spring.datasource.url=$startupUrl",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/migration/V021__create_postal_code_locations.sql",
            "spring.main.banner-mode=off",
        ).run().use { context ->
            val startedJdbc = JdbcTemplate(context.getBean(javax.sql.DataSource::class.java))
            assertEquals(34403L, startedJdbc.queryForObject("SELECT COUNT(*) FROM postal_code_locations", Long::class.java))
        }
        dataSource = DriverManagerDataSource(startupUrl, "sa", "")
        jdbc = JdbcTemplate(dataSource)
        assertEquals(34403L, count())
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM postal_code_locations WHERE zip_code='44663'", Long::class.java))
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM postal_code_locations WHERE zip_code LIKE '0%'", Long::class.java)!! > 0)
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM postal_code_locations WHERE latitude NOT BETWEEN 32 AND 40 OR longitude NOT BETWEEN 123 AND 133 OR representative_address IS NULL", Long::class.java))
        jdbc.update("UPDATE postal_code_locations SET representative_address='관리자 수정' WHERE zip_code='44663'")
        PostalCodeLocationInitializer(dataSource).run(DefaultApplicationArguments())
        assertEquals(34403L, count())
        assertEquals("관리자 수정", jdbc.queryForObject("SELECT representative_address FROM postal_code_locations WHERE zip_code='44663'", String::class.java))
    }

    @Test fun `잘못된 후속 CSV 행이 있으면 부분 적재하지 않는다`() {
        val invalidRows = listOf(
            "54321,주소,invalid,127.0,2026-10-18T00:00:00Z\n",
            "54321,주소,0,0,2026-10-18T00:00:00Z\n",
            "1234,주소,37,127,2026-10-18T00:00:00Z\n",
            first,
        )
        invalidRows.forEach { invalid ->
            assertThrows(Exception::class.java) { initialize(csv(first + invalid)) }
            assertEquals(0L, count())
        }
        initialize(csv(first))
        assertEquals(1L, count())
    }

    @Test fun `DB 제약 위반이 발생하면 이전 배치까지 모두 롤백한다`() {
        jdbc.execute("ALTER TABLE postal_code_locations ADD CONSTRAINT reject_postal CHECK (zip_code <> '54321')")
        val precedingBatch = (1..1001).joinToString("") { index ->
            "${index.toString().padStart(5, '0')},주소 $index,37.63,127.02,2026-10-18T00:00:00Z\n"
        }
        assertThrows(Exception::class.java) {
            initialize(csv(precedingBatch + "54321,주소,35.97,126.73,2026-10-18T00:00:00Z\n"))
        }
        assertEquals(0L, count())
    }

    @Test fun `두 연결이 동시에 시작해도 먼저 적재한 결과만 유지한다`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val blockedCsv = object : ByteArrayResource((header + first).toByteArray(Charsets.UTF_8)) {
            override fun getInputStream(): java.io.InputStream {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                return super.getInputStream()
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val firstRun = executor.submit { initialize(blockedCsv) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val secondRun = executor.submit {
                secondStarted.countDown()
                initialize(csv("44663,다른 주소,35.53,129.29,2026-10-18T00:00:00Z\n"))
            }
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
            release.countDown()
            firstRun.get(15, TimeUnit.SECONDS)
            secondRun.get(15, TimeUnit.SECONDS)
            assertEquals(1L, count())
            assertEquals("01234", jdbc.queryForObject("SELECT zip_code FROM postal_code_locations", String::class.java))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun initialize(resource: Resource) = PostalCodeLocationInitializer(dataSource, resource).run(DefaultApplicationArguments())
    private fun csv(rows: String) = ByteArrayResource((header + rows).toByteArray(Charsets.UTF_8))
    private fun count() = jdbc.queryForObject("SELECT COUNT(*) FROM postal_code_locations", Long::class.java)

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataSourceAutoConfiguration::class, DataSourceInitializationAutoConfiguration::class)
    @Import(PostalCodeLocationInitializer::class)
    class StartupConfig
}
