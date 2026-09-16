package hs.kr.entrydsm.platform.config

import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ModuleSchemaMigrationConfiguration {

    @Bean
    fun moduleSchemaMigrations(dataSource: DataSource): ModuleSchemaMigrations =
        ModuleSchemaMigrations(dataSource)

    companion object {
        /** Hibernate 가 스키마를 검증하기 전에 모든 모듈의 마이그레이션이 끝나 있어야 한다. */
        @Bean
        @JvmStatic
        fun entityManagerFactoryDependsOnModuleSchemaMigrations(): EntityManagerFactoryDependsOnPostProcessor =
            EntityManagerFactoryDependsOnPostProcessor(ModuleSchemaMigrations::class.java)
    }
}

/**
 * 스키마 하나에서 모듈마다 자기 마이그레이션 폴더와 이력 테이블을 따로 갖게 한다.
 *
 * 모듈은 각자 V001 부터 번호를 매기고, 모듈 사이에는 FK 가 없다. 그래서 이력을 합치지 않고
 * `db/migration/<module>` 과 `flyway_schema_history_<module>` 로 나눈다.
 * 두 번째 모듈부터는 "이력 테이블 없는 비어 있지 않은 스키마"로 보이므로 버전 0 에 기준선을 두고 V001 부터 적용한다.
 */
class ModuleSchemaMigrations(
    private val dataSource: DataSource,
    private val modules: List<String> = MODULES,
) : InitializingBean {

    override fun afterPropertiesSet() {
        modules.forEach { module -> flyway(module).migrate() }
    }

    fun flyway(module: String): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/$module")
            .table("flyway_schema_history_$module")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(true)
            .load()

    companion object {
        /** 적용 순서. 다른 모듈이 논리적으로 가리키는 쪽부터 적용한다 (identity 계정 → configuration 파일 → ...). */
        val MODULES = listOf("identity", "configuration", "application", "notification", "admin")
    }
}
