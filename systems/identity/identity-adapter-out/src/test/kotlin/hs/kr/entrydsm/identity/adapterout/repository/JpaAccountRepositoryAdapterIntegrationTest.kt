package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.config.JpaAuditingConfig
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [JpaAccountRepositoryAdapterIntegrationTest.JpaTestApplication::class])
class JpaAccountRepositoryAdapterIntegrationTest {
    @Autowired
    private lateinit var adapter: JpaAccountRepositoryAdapter

    @Autowired
    private lateinit var accountJpaRepository: AccountJpaRepository

    @Autowired
    private lateinit var studentProfileJpaRepository: StudentProfileJpaRepository

    @Before
    fun clearDatabase() {
        studentProfileJpaRepository.deleteAll()
        accountJpaRepository.deleteAll()
    }

    @Test
    fun savesAndFindsAccountAndProfileByBothQueries() {
        val saved = adapter.save(account())

        assertNotNull(saved.userId)
        assertEquals(saved.userId, adapter.findByUserId(saved.userId)?.userId)
        assertEquals(saved.userId, adapter.findByLoginId(LOGIN_ID)?.userId)
        assertEquals(BIRTHDATE, adapter.findByUserId(saved.userId)?.profile?.birthdate)
        assertNotNull(accountJpaRepository.findById(saved.userId).orElse(null)?.createdAtValue())
        assertNotNull(studentProfileJpaRepository.findByAccount_Id(saved.userId)?.createdAtValue())
    }

    @Test
    fun updatesExistingProfileAndPreservesCreatedAt() {
        val saved = adapter.save(account())
        val accountCreatedAt = requireNotNull(accountJpaRepository.findById(saved.userId).orElse(null)?.createdAtValue())
        val accountUpdatedAt = requireNotNull(accountJpaRepository.findById(saved.userId).orElse(null)?.updatedAtValue())
        val profileCreatedAt = requireNotNull(
            studentProfileJpaRepository.findByAccount_Id(saved.userId)?.createdAtValue(),
        )
        val profileUpdatedAt = requireNotNull(
            studentProfileJpaRepository.findByAccount_Id(saved.userId)?.updatedAtValue(),
        )

        saved.profile.cancel(TRANSITION_TIME)
        val updated = adapter.save(saved)
        val accountEntity = requireNotNull(accountJpaRepository.findById(saved.userId).orElse(null))
        val profileEntity = requireNotNull(studentProfileJpaRepository.findByAccount_Id(saved.userId))

        assertEquals(ApplicantStatus.CANCELED, updated.profile.applicantStatus)
        assertEquals(ApplicantStatus.CANCELED, profileEntity.applicantStatus)
        assertEquals(accountCreatedAt, accountEntity.createdAtValue())
        assertEquals(profileCreatedAt, profileEntity.createdAtValue())
        assertNotEquals(accountUpdatedAt, accountEntity.updatedAtValue())
        assertNotEquals(profileUpdatedAt, profileEntity.updatedAtValue())
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("hs.kr.entrydsm.identity.adapterout.entity")
    @EnableJpaRepositories("hs.kr.entrydsm.identity.adapterout.repository")
    @Import(JpaAuditingConfig::class, JpaAccountRepositoryAdapter::class)
    class JpaTestApplication

    private companion object {
        const val MYSQL_IMAGE = "mysql:8.4"
        const val MYSQL_PORT = 3306
        const val DATABASE = "identity_test"
        const val LOGIN_ID = "01012345678"
        val BIRTHDATE: LocalDate = LocalDate.of(2009, 3, 15)
        val CREATED_AT: Instant = Instant.parse("2026-06-11T10:00:00Z")
        val TRANSITION_TIME: Instant = Instant.parse("2026-06-11T11:00:00Z")
        lateinit var mysql: GenericContainer<Nothing>
        var mysqlStarted = false

        @JvmStatic
        @BeforeClass
        fun checkDocker() {
            assumeTrue(
                "Docker daemon is required for JPA integration tests",
                DockerClientFactory.instance().isDockerAvailable,
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDatabaseProperties(registry: DynamicPropertyRegistry) {
            mysql = GenericContainer<Nothing>(DockerImageName.parse(MYSQL_IMAGE))
                .withEnv("MYSQL_DATABASE", DATABASE)
                .withEnv("MYSQL_USER", "identity")
                .withEnv("MYSQL_PASSWORD", "identity")
                .withEnv("MYSQL_ROOT_PASSWORD", "root")
                .withExposedPorts(MYSQL_PORT)
            mysql.start()
            mysqlStarted = true
            registry.add("spring.datasource.url") {
                "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(MYSQL_PORT)}/$DATABASE?useSSL=false&serverTimezone=UTC"
            }
            registry.add("spring.datasource.username") { "identity" }
            registry.add("spring.datasource.password") { "identity" }
            registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.jpa.properties.hibernate.dialect") { "org.hibernate.dialect.MySQLDialect" }
        }

        @JvmStatic
        @AfterClass
        fun stopDatabase() {
            if (mysqlStarted) mysql.stop()
        }
    }

    private fun account(): Account = Account.create(
        userId = 1L,
        loginId = LOGIN_ID,
        passwordHash = PasswordHash.fromEncoded("encoded-password"),
        role = Role.STUDENT,
        status = AccountStatus.ACTIVE,
        profile = StudentProfile(
            name = "홍길동",
            phone = LOGIN_ID,
            birthdate = BIRTHDATE,
            signupType = SignupType.SELF,
            applicantStatus = ApplicantStatus.SUBMITTED,
            passStatus = PassStatus.NOT_ANNOUNCED,
            submittedAt = CREATED_AT,
            updatedAt = CREATED_AT,
        ),
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
    )
}
