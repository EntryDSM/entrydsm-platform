package hs.kr.entrydsm.application.administration.adapterout.recovery

import hs.kr.entrydsm.application.adapterout.entity.*
import hs.kr.entrydsm.application.adapterout.repository.*
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.*
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.application.domain.enum.*
import hs.kr.entrydsm.application.domain.model.*
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@RunWith(SpringRunner::class)
@DataJpaTest
@org.springframework.test.context.ActiveProfiles("test")
@ContextConfiguration(classes = [ScoreRecoveryServiceTest.Config::class])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScoreRecoveryServiceTest {
    @Autowired private lateinit var service: ScoreRecoveryService
    @Autowired private lateinit var applicants: ApplicantJpaRepository
    @Autowired private lateinit var persistence: ApplicantRepository
    @Autowired private lateinit var outbox: ApplicantStatusOutboxJpaRepository
    @Autowired private lateinit var failing: FailingOutbox
    @Autowired private lateinit var transactions: org.springframework.transaction.PlatformTransactionManager

    @Before fun reset() { failing.fail = false; outbox.deleteAll(); applicants.deleteAll() }

    @Test fun dryRunAndInvalidEvidenceNeverChangeStoredData() {
        val proof = fixture()
        assertEquals(3L, service.recover(proof, false))
        assertNull(persistence.findById(proof.applicantId)!!.academicRecord!!.gedScores)
        assertEquals(0L, outbox.count())
        assertThrows(IllegalArgumentException::class.java) { service.recover(proof.copy(artifactSha256 = "0".repeat(64)), true) }
        assertThrows(IllegalArgumentException::class.java) { service.recover(proof.copy(verifiedBy = ""), true) }
        assertThrows(IllegalArgumentException::class.java) { service.recover(proof.copy(artifactPath = "missing-recovery-proof.local.pdf"), true) }
        assertThrows(IllegalArgumentException::class.java) { service.recover(proof.copy(expectedStatusVersion = 4), true) }
        assertThrows(IllegalArgumentException::class.java) { service.recover(proof.copy(academicRecord = AcademicRecord()), true) }
        assertNull(persistence.findById(proof.applicantId)!!.academicRecord!!.gedScores)
        assertEquals(3L, persistence.findById(proof.applicantId)!!.statusVersion)
        assertEquals(0L, outbox.count())
    }

    @Test fun restoresScoresAndOutboxTogetherPreservingApplicationAndRecordIdentity() {
        val proof = fixture()
        val original = persistence.findById(proof.applicantId)!!
        assertEquals(4L, service.recover(proof, true))
        val result = persistence.findById(proof.applicantId)!!
        assertEquals(original.id, result.id)
        assertEquals(original.accountId, result.accountId)
        assertEquals(original.status, result.status)
        assertEquals(original.submittedAt, result.submittedAt)
        assertEquals(original.examineeNumber, result.examineeNumber)
        assertEquals(original.passStatus, result.passStatus)
        assertEquals(original.passResultType, result.passResultType)
        assertEquals(PassResultStatus.PASS, result.passStatus)
        assertEquals(ResultType.FINAL, result.passResultType)
        assertEquals(original.announcedAt, result.announcedAt)
        assertEquals(proof.academicRecord.gedScores, result.academicRecord!!.gedScores)
        assertNotNull(result.totalScoreUpdatedAt)
        assertEquals(1L, outbox.count())
        val stored = applicants.findAllByAccountIdIn(listOf(proof.accountId)).single()
        assertEquals(proof.academicRecordId, stored.academicRecord!!.id)
        assertEquals(proof.academicRecordId, stored.academicRecord!!.gedScores!!.academicRecordId)
    }

    @Test fun outboxFailureRollsBackScoreAndTotalUpdate() {
        val proof = fixture()
        failing.fail = true
        assertThrows(IllegalStateException::class.java) { service.recover(proof, true) }
        val unchanged = persistence.findById(proof.applicantId)!!
        assertNull(unchanged.academicRecord!!.gedScores)
        assertEquals(3L, unchanged.statusVersion)
        assertEquals(158.0, unchanged.totalScore!!, 0.0)
        assertEquals(0L, outbox.count())
    }

    private fun fixture(): ScoreRecoveryEvidence {
        val submitted = LocalDateTime.of(2026, 9, 27, 23, 57)
        val entity = ApplicantJpaEntity.from(Applicant(id = 0, accountId = 10,
            admissionType = AdmissionType.REGULAR, graduationType = GraduationType.GED,
            academicRecord = AcademicRecord(), status = ApplicantStatus.ARRIVAL, totalScore = 158.0,
            submittedAt = submitted, statusVersion = 3, examineeNumber = "TEST0001"))
        val saved = org.springframework.transaction.support.TransactionTemplate(transactions).execute {
            applicants.saveAndFlush(entity).also { persisted ->
                persisted.passResults.add(PassResultJpaEntity(PassResultId(persisted.id, ResultType.FINAL), persisted,
                    result = PassResultStatus.PASS, processedAt = submitted))
                applicants.saveAndFlush(persisted)
            }
        }!!
        val artifact = Files.createTempFile("score-recovery-test-", ".txt")
        artifact.toFile().deleteOnExit()
        Files.writeString(artifact, "테스트 전용 검증 증빙")
        val sha = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)).joinToString("") { "%02x".format(it) }
        return ScoreRecoveryEvidence(saved.id!!, saved.accountId, saved.academicRecord!!.id!!, 3,
            GraduationType.GED, AdmissionType.REGULAR, EvidenceSource.OFFICIAL_DOCUMENT, "test-record", "test-operator",
            artifact.toString(), sha, AcademicRecord(gedScores = GedScores(80, 81, 82, 83, 84, 85, 86)))
    }

    class FailingOutbox(private val delegate: ApplicantStatusOutboxAdapter) : ApplicantStatusEventOutbox {
        var fail = false
        override fun add(event: ApplicantStatusChanged) { delegate.add(event); if (fail) error("테스트 outbox 실패") }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = [ApplicantJpaEntity::class])
    @EnableJpaRepositories(basePackageClasses = [ApplicantJpaRepository::class])
    @Import(ApplicantPersistenceAdapter::class, ScoreRecoveryService::class)
    class Config {
        @Bean fun outbox(repository: ApplicantStatusOutboxJpaRepository) = FailingOutbox(ApplicantStatusOutboxAdapter(repository,
            SnapshotCipher("test", mapOf("test" to "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="))))
        @Bean fun application(repository: ApplicantRepository, outbox: ApplicantStatusEventOutbox): ApplicationPort =
            ApplicationCommandService(repository, ApplicationPeriodReader { null }, AccountPhoneValidator { _, _ -> false }, outbox)
    }
}
