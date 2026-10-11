package hs.kr.entrydsm.application.config

import java.time.LocalDateTime
import java.util.UUID
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
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

class ScheduleInitializerTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcTemplate
    private val defaults = listOf(
        Seed("원서 접수", "2026-10-19T09:00:00", "2026-10-22T17:00:00"),
        Seed("1차 합격 발표", "2026-10-26T15:00:00", "2026-10-26T15:00:00"),
        Seed("면접", "2026-10-30T00:00:00", "2026-10-30T00:00:00"),
        Seed("최종 합격 발표", "2026-11-04T10:00:00", "2026-11-04T10:00:00"),
    )

    @Before fun schema() {
        dataSource = DriverManagerDataSource("jdbc:h2:mem:schedule_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")
        jdbc = JdbcTemplate(dataSource)
        ResourceDatabasePopulator(ClassPathResource("db/migration/V016__create_schedule.sql")).execute(dataSource)
    }

    @Test fun `빈 DB에 서울 현지 시각의 초기 일정 4개를 추가하고 발표시각을 조회한다`() {
        initialize()
        assertEquals(defaults.associate { it.title to (it.start to it.end) }, rows().associate { it.title to (it.start to it.end) })
        assertEquals(LocalDateTime.of(2026, 10, 26, 15, 0), rows().single { it.title == "1차 합격 발표" }.start)
    }

    @Test fun `일부 일정이 있으면 누락된 제목만 추가하고 기존 ID와 수정 시각을 보존한다`() {
        insert(99L, "원서 접수", "2026-10-18T08:30:00", "2026-10-23T18:30:00")
        insert(1L, "관리자 추가 일정", "2026-10-01T10:00:00", "2026-10-01T11:00:00")
        val existing = rows()
        initialize()
        assertEquals(5, rows().size)
        assertTrue(rows().containsAll(existing))
        defaults.drop(1).forEach { seed ->
            val row = rows().single { it.title == seed.title }
            assertEquals(seed.start, row.start)
            assertEquals(seed.end, row.end)
        }
    }

    @Test fun `모든 제목이 있으면 어떤 행도 추가하거나 수정하지 않는다`() {
        defaults.forEachIndexed { index, seed ->
            insert(40L + index, seed.title, "2026-09-01T09:00:00", "2026-09-02T17:00:00")
        }
        val before = rows()
        initialize()
        assertEquals(before, rows())
    }

    @Test fun `초기화 객체를 다시 만들어 실행해도 기존 일정과 관리자 수정이 유지된다`() {
        initialize()
        jdbc.update("UPDATE schedule SET start_at = ?, end_at = ? WHERE title = ?",
            LocalDateTime.of(2026, 10, 27, 16, 0), LocalDateTime.of(2026, 10, 27, 16, 0), "1차 합격 발표")
        val before = rows()
        initialize()
        initialize()
        assertEquals(before, rows())
    }

    @Test fun `기존 ID 1부터 4가 다른 일정에 사용돼도 초기 제목 4개를 추가한다`() {
        (1L..4L).forEach { insert(it, "기존 일정 $it", "2026-09-01T09:00:00", "2026-09-01T17:00:00") }
        val existing = rows()
        initialize()
        assertEquals(8, rows().size)
        assertTrue(rows().containsAll(existing))
        assertEquals(defaults.map { it.title }.toSet(), rows().filter { it.id > 4 }.map { it.title }.toSet())
    }

    @Test fun `다음 시작 시 삭제된 일정만 복구하고 다른 ID와 시각은 유지한다`() {
        initialize()
        jdbc.update("DELETE FROM schedule WHERE title = ?", "면접")
        val remaining = rows()
        initialize()
        assertEquals(4, rows().size)
        assertTrue(rows().containsAll(remaining))
        val restored = rows().single { it.title == "면접" }
        assertEquals(LocalDateTime.of(2026, 10, 30, 0, 0), restored.start)
        assertEquals(restored.start, restored.end)
    }

    @Test fun `실제 Spring 시작은 스키마 준비 후 관리자 기능이 꺼져 있어도 일정을 초기화한다`() {
        SpringApplicationBuilder(StartupConfig::class.java).web(WebApplicationType.NONE).properties(
            "spring.config.location=optional:classpath:schedule-initializer-empty.yml",
            "spring.datasource.url=jdbc:h2:mem:startup_${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/migration/V016__create_schedule.sql",
            "application.administration.enabled=false",
            "spring.main.banner-mode=off",
        ).run().use { context ->
            val startedJdbc = JdbcTemplate(context.getBean(javax.sql.DataSource::class.java))
            assertEquals(4L, startedJdbc.queryForObject("SELECT COUNT(*) FROM schedule", Long::class.java))
            assertEquals(LocalDateTime.of(2026, 10, 26, 15, 0), startedJdbc.queryForObject(
                "SELECT start_at FROM schedule WHERE title = ?", LocalDateTime::class.java, "1차 합격 발표"))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(DataSourceAutoConfiguration::class, DataSourceInitializationAutoConfiguration::class)
    @Import(ScheduleInitializer::class)
    class StartupConfig

    private fun initialize() = ScheduleInitializer(dataSource).run(DefaultApplicationArguments())
    private fun insert(id: Long, title: String, start: String, end: String) =
        jdbc.update("INSERT INTO schedule (id, title, start_at, end_at) VALUES (?, ?, ?, ?)",
            id, title, LocalDateTime.parse(start), LocalDateTime.parse(end))
    private fun rows() = jdbc.query("SELECT id, title, start_at, end_at FROM schedule ORDER BY id") { row, _ ->
        Row(row.getLong("id"), row.getString("title"), row.getObject("start_at", LocalDateTime::class.java),
            row.getObject("end_at", LocalDateTime::class.java))
    }
    private data class Row(val id: Long, val title: String, val start: LocalDateTime, val end: LocalDateTime)
    private data class Seed(val title: String, val start: LocalDateTime, val end: LocalDateTime) {
        constructor(title: String, start: String, end: String) : this(title, LocalDateTime.parse(start), LocalDateTime.parse(end))
    }
}
