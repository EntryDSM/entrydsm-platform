package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.adapterout.config.JpaAuditingConfig
import hs.kr.entrydsm.identity.adapterout.entity.AccountJpaEntity
import hs.kr.entrydsm.identity.adapterout.entity.StudentProfileJpaEntity
import hs.kr.entrydsm.identity.adapterout.persistence.AccountApplicationDataPersistenceAdapter
import hs.kr.entrydsm.identity.adapterout.security.AesGcmPersonalDataEncryptor
import hs.kr.entrydsm.identity.adapterout.security.HmacLoginIdHasher
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent
import hs.kr.entrydsm.identity.domain.enum.AccountStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.PassStatus
import hs.kr.entrydsm.identity.domain.enum.Role
import hs.kr.entrydsm.identity.domain.enum.SignupType
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.Account
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import hs.kr.entrydsm.identity.domain.model.StudentProfile
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors
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
import org.springframework.boot.persistence.autoconfigure.EntityScan
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
    private lateinit var applicationDataAdapter: AccountApplicationDataPersistenceAdapter

    @Autowired
    private lateinit var accountJpaRepository: AccountJpaRepository

    @Autowired
    private lateinit var studentProfileJpaRepository: StudentProfileJpaRepository

    @Autowired
    private lateinit var applicationProjectionJpaRepository: ApplicationProjectionJpaRepository

    @Autowired
    private lateinit var identityOutboxJpaRepository: IdentityOutboxJpaRepository

    @Before
    fun clearDatabase() {
        identityOutboxJpaRepository.deleteAll()
        applicationProjectionJpaRepository.deleteAll()
        studentProfileJpaRepository.deleteAll()
        accountJpaRepository.deleteAll()
    }

    @Test
    fun savesAndFindsAccountAndProfileByBothQueries() {
        val saved = adapter.save(account())

        assertNotNull(saved.userId)
        val byUserId = requireNotNull(adapter.findByUserId(saved.userId))
        val byLoginId = requireNotNull(adapter.findByLoginId(LOGIN_ID))
        assertEquals(saved.userId, byUserId.userId)
        assertEquals(saved.loginId, byUserId.loginId)
        assertEquals(saved.profile.name, byUserId.profile.name)
        assertEquals(saved.profile.phone, byUserId.profile.phone)
        assertEquals(saved.loginId, byLoginId.loginId)
        assertEquals(saved.profile.name, byLoginId.profile.name)
        assertEquals(saved.profile.phone, byLoginId.profile.phone)
        assertEquals(BIRTHDATE, byUserId.profile.birthdate)
        val persistedAccount = requireNotNull(accountJpaRepository.findById(saved.userId).orElse(null))
        val persistedProfile = requireNotNull(studentProfileJpaRepository.findByAccount_Id(saved.userId))
        assertNotEquals(LOGIN_ID, persistedAccount.loginIdHash)
        assertNotEquals(LOGIN_ID, persistedAccount.loginIdEncrypted)
        assertNotEquals("홍길동", persistedProfile.nameEncrypted)
        assertNotEquals(LOGIN_ID, persistedProfile.phoneEncrypted)
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

    @Test
    fun applicationDataAdapterUsesProjectionAndWritesOutboxForCancellation() {
        val saved = adapter.save(account())
        applicationDataAdapter.create(saved.userId, CREATED_AT)
        assertEquals(true, applicationDataAdapter.consume(
            ApplicationStateChangedEvent(
                eventId = "application-submitted-1",
                userId = saved.userId,
                version = 1,
                applicantStatus = ApplicantStatus.SUBMITTED,
                submittedAt = CREATED_AT,
                passStatus = PassStatus.NOT_ANNOUNCED,
                announcedAt = null,
                occurredAt = CREATED_AT,
            ),
        ))

        val outboxCountBeforeRejectedEvents = identityOutboxJpaRepository.count()
        assertEquals(false, applicationDataAdapter.consume(
            ApplicationStateChangedEvent(
                eventId = "application-submitted-1",
                userId = saved.userId,
                version = 2,
                applicantStatus = ApplicantStatus.SUBMITTED,
                submittedAt = CREATED_AT,
                passStatus = PassStatus.NOT_ANNOUNCED,
                announcedAt = null,
                occurredAt = CREATED_AT,
            ),
        ))
        assertEquals(false, applicationDataAdapter.consume(
            ApplicationStateChangedEvent(
                eventId = "application-submitted-old",
                userId = saved.userId,
                version = 0,
                applicantStatus = ApplicantStatus.NONE,
                submittedAt = null,
                passStatus = PassStatus.NOT_ANNOUNCED,
                announcedAt = null,
                occurredAt = CREATED_AT,
            ),
        ))
        assertEquals(outboxCountBeforeRejectedEvents, identityOutboxJpaRepository.count())

        val beforeCancel = requireNotNull(applicationDataAdapter.findByUserId(saved.userId))
        assertEquals(ApplicantStatus.SUBMITTED, beforeCancel.applicantStatus)
        assertEquals(PassStatus.NOT_ANNOUNCED, beforeCancel.passStatus)

        val canceled = applicationDataAdapter.cancel(saved.userId, "개인 사유", TRANSITION_TIME)
        val persisted = requireNotNull(adapter.findByUserId(saved.userId))

        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals(TRANSITION_TIME, canceled.updatedAt)
        assertEquals(ApplicantStatus.SUBMITTED, persisted.profile.applicantStatus)
        assertEquals(1, identityOutboxJpaRepository.count())
    }

    @Test
    fun concurrentCancellationAllowsOnlyOneStateTransition() {
        val saved = adapter.save(account())
        applicationDataAdapter.create(saved.userId, CREATED_AT)
        applicationDataAdapter.consume(
            ApplicationStateChangedEvent(
                eventId = "application-concurrent-submitted",
                userId = saved.userId,
                version = 1,
                applicantStatus = ApplicantStatus.SUBMITTED,
                submittedAt = CREATED_AT,
                passStatus = PassStatus.NOT_ANNOUNCED,
                announcedAt = null,
                occurredAt = CREATED_AT,
            ),
        )

        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { index ->
                executor.submit(java.util.concurrent.Callable {
                    try {
                        applicationDataAdapter.cancel(
                            saved.userId,
                            "concurrent-$index",
                            TRANSITION_TIME,
                        )
                        null
                    } catch (exception: IdentityDomainException) {
                        exception
                    }
                })
            }
            val failures = futures.map { it.get() }.filterIsInstance<IdentityDomainException>()

            assertEquals(1, failures.size)
            assertEquals(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED, failures.single().errorCode)
            assertEquals(1, identityOutboxJpaRepository.count())
        } finally {
            executor.shutdownNow()
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan("hs.kr.entrydsm.identity.adapterout.entity")
    @EnableJpaRepositories("hs.kr.entrydsm.identity.adapterout.repository")
    @Import(
        JpaAuditingConfig::class,
        AesGcmPersonalDataEncryptor::class,
        HmacLoginIdHasher::class,
        JpaAccountRepositoryAdapter::class,
        AccountApplicationDataPersistenceAdapter::class,
    )
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
                .withExposedPorts(MYSQL_PORT)
            mysql.addEnv("MYSQL_DATABASE", DATABASE)
            mysql.addEnv("MYSQL_USER", "identity")
            mysql.addEnv("MYSQL_PASSWORD", "identity")
            mysql.addEnv("MYSQL_ROOT_PASSWORD", "root")
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
            registry.add("security.pii.login-id-hash-key") { "integration-test-login-id-hash-key" }
            registry.add("security.pii.encryption-key-base64") {
                "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
            }
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
