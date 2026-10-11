package hs.kr.entrydsm.application.config

import javax.sql.DataSource
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.stereotype.Component

@Component
@DependsOnDatabaseInitialization
class ScheduleInitializer(private val dataSource: DataSource) : ApplicationRunner {
    /** 스키마 준비 후 제목이 없는 일정만 보충하고 기존 일정은 유지한다. */
    override fun run(args: ApplicationArguments) {
        ResourceDatabasePopulator(ClassPathResource("schedule.sql")).execute(dataSource)
    }
}
