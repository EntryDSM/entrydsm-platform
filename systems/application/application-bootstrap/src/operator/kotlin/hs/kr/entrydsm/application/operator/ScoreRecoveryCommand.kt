package hs.kr.entrydsm.application.operator

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.adapterout.entity.PersonalDataConverter
import hs.kr.entrydsm.application.adapterout.repository.*
import hs.kr.entrydsm.application.administration.adapterout.recovery.ScoreRecoveryEvidence
import hs.kr.entrydsm.application.administration.adapterout.recovery.ScoreRecoveryService
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.*
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.application.config.SnapshotCipherConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = [ApplicantJpaEntity::class])
@EnableJpaRepositories(basePackageClasses = [ApplicantJpaRepository::class])
@Import(ApplicantPersistenceAdapter::class, ApplicantStatusOutboxAdapter::class,
    PersonalDataConverter::class, SnapshotCipherConfiguration::class)
class ScoreRecoveryConfiguration {
    @Bean
    fun application(applicants: ApplicantRepository, outbox: ApplicantStatusEventOutbox): ApplicationPort =
        ApplicationCommandService(applicants, ApplicationPeriodReader { error("OFFLINE_READ_ONLY") },
            AccountPhoneValidator { _, _ -> error("OFFLINE_READ_ONLY") }, outbox)

    @Bean
    fun recovery(locked: ApplicantJpaRepository, applicants: ApplicantRepository,
                 application: ApplicationPort, outbox: ApplicantStatusEventOutbox) =
        ScoreRecoveryService(locked, applicants, application, outbox)
}

fun main(args: Array<String>) {
    try {
        require(args.size == 2 && args[0] in setOf("--dry-run", "--apply")) { "RECOVERY_USAGE" }
        require(args[0] != "--apply" || System.getenv("RECOVERY_WRITERS_STOPPED") == "true") { "RECOVERY_FREEZE_REQUIRED" }
        val path = Path.of(args[1])
        require(Files.size(path) <= 64 * 1024) { "RECOVERY_PROOF_TOO_LARGE" }
        val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
        val content = Files.readString(path)
        val tree = mapper.readTree(content)
        // AcademicRecord의 기본값으로 증빙에 없는 출결·가산점을 만들어내지 않는다.
        val record = requireNotNull(tree.get("academicRecord"))
        for (field in listOf("absentCount", "lateCount", "earlyLeaveCount", "classAbsenceCount", "volunteerTime",
                            "isDsmAlgorithmAwarded", "isProgrammingCertified", "subjectGrades", "gedScores")) {
            require(record.has(field)) { "RECOVERY_INCOMPLETE_EVIDENCE" }
        }
        val proof = mapper.readValue(content, ScoreRecoveryEvidence::class.java)
        // 별도 컨텍스트에는 HTTP/gRPC 서버, 스케줄러, Redis relay를 등록하지 않는다.
        SpringApplicationBuilder(ScoreRecoveryConfiguration::class.java).web(WebApplicationType.NONE)
            .properties(mapOf("spring.flyway.enabled" to "false", "spring.main.banner-mode" to "off",
                "logging.level.root" to "OFF", "spring.jpa.show-sql" to "false"))
            .logStartupInfo(false)
            .run("--spring.flyway.enabled=false", "--spring.jpa.show-sql=false", "--logging.level.root=OFF",
                "--logging.level.org.hibernate.SQL=OFF", "--logging.level.org.hibernate.orm.jdbc.bind=OFF").use { context ->
                val version = context.getBean(ScoreRecoveryService::class.java).recover(proof, args[0] == "--apply")
                println("{\"success\":true,\"applied\":${args[0] == "--apply"},\"applicantId\":${proof.applicantId},\"version\":$version}")
            }
    } catch (error: Exception) {
        // 성적·개인정보·접속 정보를 포함할 수 있는 예외 메시지는 출력하지 않는다.
        System.err.println("{\"success\":false,\"error\":\"RECOVERY_FAILED\",\"exception\":\"${error.javaClass.simpleName}\"}")
        exitProcess(1)
    }
}
