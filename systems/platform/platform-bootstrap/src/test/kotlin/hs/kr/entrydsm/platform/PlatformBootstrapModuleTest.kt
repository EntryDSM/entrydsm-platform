package hs.kr.entrydsm.platform

import hs.kr.entrydsm.platform.config.ModuleSchemaMigrations
import hs.kr.entrydsm.platform.config.PlatformInfrastructureConfiguration
import java.nio.charset.StandardCharsets
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.jdbc.datasource.SimpleDriverDataSource

class PlatformBootstrapModuleTest {
    private val classLoader = javaClass.classLoader

    @Test
    fun onlyThePlatformShipsRootApplicationConfiguration() {
        // 같은 경로의 application.yaml 이 둘 이상이면 먼저 읽힌 것만 적용되고 나머지는 조용히 무시된다.
        val locations = classLoader.getResources("application.yaml").toList()

        assertEquals(locations.toString(), 1, locations.size)
    }

    @Test
    fun importsExistingModuleConfigurations() {
        val imports = Regex("classpath:(modules/[a-z]+\\.yaml)")
            .findAll(rootConfiguration())
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("identity", "application", "configuration", "admin", "observability").map { "modules/$it.yaml" }, imports)
        imports.forEach { assertNotNull(it, classLoader.getResource(it)) }
    }

    @Test
    fun validatesSchemaAndInitializesEagerly() {
        val configuration = rootConfiguration()

        assertTrue(configuration.contains("ddl-auto: validate"))
        assertTrue(configuration.contains("open-in-view: false"))
        assertTrue(configuration.contains("lazy-initialization: false"))
        assertTrue(configuration.contains("url: \${DB_URL}"))
    }

    @Test
    fun everyModuleShipsMigrationsInItsOwnFolder() {
        val firstMigrations = mapOf(
            "identity" to "V001__create_identity_tables.sql",
            "configuration" to "V001__create_configuration_tables.sql",
            "application" to "V001__create_application_tables.sql",
            "notification" to "V028__create_notification_tables.sql",
            "admin" to "V001__create_admin_tables.sql",
        )

        assertEquals(firstMigrations.keys.toList(), ModuleSchemaMigrations.MODULES)
        firstMigrations.forEach { (module, migration) ->
            assertNotNull(module, classLoader.getResource("db/migration/$module/$migration"))
        }
        // 공용 폴더에 남은 마이그레이션이 있으면 모듈 이력 테이블 어디에도 기록되지 않는다.
        assertTrue(classLoader.getResources("db/migration/V001__create_identity_tables.sql").toList().isEmpty())
    }

    @Test
    fun keepsMigrationHistoryPerModule() {
        val flyway = ModuleSchemaMigrations(SimpleDriverDataSource()).flyway("application").configuration

        assertEquals("flyway_schema_history_application", flyway.table)
        assertEquals(listOf("classpath:db/migration/application"), flyway.locations.map { it.toString() })
        assertTrue(flyway.isBaselineOnMigrate)
        assertEquals("0", flyway.baselineVersion.version)
        assertTrue(flyway.isCleanDisabled)
    }

    @Test
    fun sharesOneUtcClock() {
        assertEquals(ZoneOffset.UTC, PlatformInfrastructureConfiguration().clock().zone)
    }

    private fun rootConfiguration(): String =
        requireNotNull(classLoader.getResourceAsStream("application.yaml"))
            .use { it.readBytes().toString(StandardCharsets.UTF_8) }
}
