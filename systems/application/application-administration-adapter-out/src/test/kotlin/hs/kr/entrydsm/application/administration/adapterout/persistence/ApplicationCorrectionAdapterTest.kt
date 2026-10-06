package hs.kr.entrydsm.application.administration.adapterout.persistence

import hs.kr.entrydsm.admin.domain.command.*
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.*
import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.adapterout.repository.*
import hs.kr.entrydsm.application.administration.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.application.administration.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.application.administration.application.ApplicationCorrectionService
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.application.domain.enum.*
import hs.kr.entrydsm.application.domain.model.*
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
@ContextConfiguration(classes = [ApplicationCorrectionAdapterTest.Config::class])
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplicationCorrectionAdapterTest {
    @Autowired private lateinit var service: ApplicationCorrectionService
    @Autowired private lateinit var applicants: ApplicantJpaRepository
    @Autowired private lateinit var persistence: ApplicantRepository
    @Autowired private lateinit var outbox: ApplicantStatusOutboxJpaRepository
    @Autowired private lateinit var audits: ApplicationCorrectionAuditJpaRepository
    @Autowired private lateinit var screening: ScreeningJpaRepository
    @Autowired private lateinit var schoolCodes: InstitutionCodeJpaRepository
    @Autowired private lateinit var administration: LocalApplicantDataAdapter
    @Autowired private lateinit var applicationApi: ApplicationPort
    @Autowired private lateinit var failing: FailingWrites

    @Before fun reset() {
        failing.failAudit = false; failing.failOutbox = false
        audits.deleteAll(); outbox.deleteAll(); screening.deleteAll(); applicants.deleteAll()
    }

    @Test fun updatesTypeScoreAddressAndAuditInOneTransaction() {
        val original = fixture()
        val result = service.correct(command(original, ApplicationFormChanges(admissionType = "MEISTER", addressBase = "수정 주소")), "operator-10")
        val saved = persistence.findById(original.id)!!
        assertEquals(4L, result.version)
        assertEquals(110.0, saved.totalScore!!, 0.0)
        assertEquals("수정 주소", saved.addressBase)
        assertEquals(original.accountId, saved.accountId)
        assertEquals(original.submittedAt, saved.submittedAt)
        assertEquals(original.status, saved.status)
        assertEquals(original.examineeNumber, saved.examineeNumber)
        assertEquals(original.passStatus, saved.passStatus)
        assertEquals(1L, outbox.count())
        val audit = audits.findAll().single()
        assertEquals("operator-10", audit.editorId)
        assertEquals("입력 오류 정정", audit.reason)
        assertEquals("addressBase,admissionType", audit.changedFields)
        assertFalse(audit.changedFields.contains("수정 주소"))
    }

    @Test fun convertsGedToSchoolWithRequiredGradesAndKeepsRecordIdentity() {
        val original = fixture()
        schoolCodes.saveAndFlush(hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity(
            "SCHOOL1", "테스트학교", "테스트학교", null, "학교 주소", null, null))
        val recordId = applicants.findAllByAccountIdIn(listOf(original.accountId)).single().academicRecord!!.id
        service.correct(command(original, ApplicationFormChanges(
            graduationType = "PROSPECTIVE", graduationDate = "2027-02",
            school = SchoolCorrection("SCHOOL1", "테스트학교", "30101", "042-123-4567", "테스트교사"),
            academicRecord = AcademicRecordCorrection(0, 0, 0, 0, 15, false, false,
                mapOf("THIRD_GRADE_FIRST_SEMESTER" to SubjectGradesCorrection("A", "A", "A", "A", "A", "A", "A"))),
        )), "operator-10")
        val saved = persistence.findById(original.id)!!
        assertNull(saved.academicRecord!!.gedScores)
        assertEquals(170.0, saved.totalScore!!, 0.0)
        assertEquals("테스트학교", saved.middleSchoolInfo!!.schoolName)
        assertEquals("학교 주소", saved.middleSchoolInfo!!.schoolAddress)
        assertEquals("학교 주소", failing.lastEvent!!.applicationForm!!.middleSchool!!.schoolAddress)
        assertEquals(recordId, applicants.findAllByAccountIdIn(listOf(original.accountId)).single().academicRecord!!.id)
    }

    @Test fun invalidInputMissingApplicantStatusAndVersionNeverWrite() {
        val original = fixture()
        val valid = command(original, ApplicationFormChanges(introduction = "수정 자기소개서"))
        assertError(ErrorCode.APPLICANT_NOT_FOUND) { service.correct(valid.copy(applicantId = Long.MAX_VALUE), "operator-10") }
        assertError(ErrorCode.AUTH_UNAUTHORIZED) { service.correct(valid, "") }
        assertError(ErrorCode.INVALID_APPLICATION_CORRECTION) {
            service.correct(command(original, ApplicationFormChanges(academicRecord = AcademicRecordCorrection(0, 0, 0, 0, 0, false, false))), "operator-10")
        }
        assertError(ErrorCode.INVALID_APPLICATION_CORRECTION) {
            service.correct(command(original, ApplicationFormChanges(introduction = "")), "operator-10")
        }
        assertEquals(3L, persistence.findById(original.id)!!.statusVersion)
        assertEquals(0L, audits.count()); assertEquals(0L, outbox.count())
        screening.saveAndFlush(ScreeningJpaEntity(applicantId = original.id, status = hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_PASS))
        assertError(ErrorCode.INVALID_STATUS_TRANSITION) { service.correct(valid, "operator-10") }
        screening.deleteAll()
        applicants.findById(original.id).get().also { it.status = ApplicantStatus.DRAFT; applicants.saveAndFlush(it) }
        assertError(ErrorCode.INVALID_STATUS_TRANSITION) { service.correct(valid, "operator-10") }
    }

    @Test fun outboxAndAuditFailuresRollBackAllChanges() {
        val original = fixture()
        val request = command(original, ApplicationFormChanges(admissionType = "MEISTER"))
        failing.failOutbox = true
        assertThrows(IllegalStateException::class.java) { service.correct(request, "operator-10") }
        assertUnchanged(original)
        failing.failOutbox = false; failing.failAudit = true
        assertThrows(IllegalStateException::class.java) { service.correct(request, "operator-10") }
        assertUnchanged(original)
    }

    @Test fun simultaneousCorrectionsUseServerManagedVersions() {
        val original = fixture()
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf("수정 A", "수정 B").map { text -> pool.submit<String> {
                assertTrue(gate.await(10, TimeUnit.SECONDS))
                try {
                    service.correct(command(original, ApplicationFormChanges(introduction = text)), "operator-10")
                    "SUCCESS"
                } catch (exception: AdminDomainException) { exception.errorCode.name }
            } }
            gate.countDown()
            assertEquals(listOf("SUCCESS", "SUCCESS"), futures.map { it.get(30, TimeUnit.SECONDS) })
            assertEquals(5L, persistence.findById(original.id)!!.statusVersion)
            assertEquals(2L, audits.count()); assertEquals(2L, outbox.count())
        } finally { pool.shutdownNow() }
    }

    @Test fun applicantApiStillRejectsSubmittedApplication() {
        val original = fixture()
        val applicantService = ApplicationCommandService(persistence,
            ApplicationPeriodReader { java.time.Instant.now().minusSeconds(60)..java.time.Instant.now().plusSeconds(60) },
            AccountPhoneValidator { _, _ -> true })
        assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) {
            applicantService.updateIntroduction(original.accountId, "지원자 수정 시도")
        }
        assertUnchanged(original)
    }

    private fun assertUnchanged(original: Applicant) {
        val saved = persistence.findById(original.id)!!
        assertEquals(original.admissionType, saved.admissionType)
        assertEquals(original.totalScore, saved.totalScore)
        assertEquals(original.statusVersion, saved.statusVersion)
        assertEquals(0L, audits.count()); assertEquals(0L, outbox.count())
    }

    @Test fun screeningCannotSaveResultsCalculatedBeforeCorrection() {
        val original = fixture()
        val stale = administration.findById(original.id)!!
        service.correct(command(original, ApplicationFormChanges(admissionType = "MEISTER")), "operator-10")
        assertError(ErrorCode.APPLICATION_VERSION_CONFLICT) {
            administration.save(stale.copy(status = hs.kr.entrydsm.admin.domain.enum.ApplicantStatus.FIRST_PASS))
        }
        assertEquals(0L, screening.count())
        assertEquals(4L, persistence.findById(original.id)!!.statusVersion)
        assertEquals(PassResultStatus.PENDING, persistence.findById(original.id)!!.passStatus)
    }

    @Test fun arrivalFlowUsesVersionChangedByOriginalApplication() {
        val original = fixture()
        val management = hs.kr.entrydsm.application.administration.application.ApplicantService(
            administration, administration, administration, DistancePort { 0 }, java.time.Clock.systemUTC())
        management.updateArrival(UpdateArrivalCommand(original.id, true))
        assertEquals(ApplicantStatus.ARRIVAL, persistence.findById(original.id)!!.status)
        assertEquals(4L, persistence.findById(original.id)!!.statusVersion)
        assertTrue(screening.findById(original.id).get().isArrived)
    }

    @Test fun issuedNumberSurvivesTypeRegionAndAddressCorrectionAndIsNotReused() {
        val initial = fixture()
        val original = applicants.findById(initial.id).get().also {
            it.examineeNumber = "31007"; it.status = ApplicantStatus.ARRIVAL
        }.let { applicants.saveAndFlush(it).toDomain() }
        screening.saveAndFlush(ScreeningJpaEntity(applicantId = original.id, examineeNumber = "31007", isArrived = true))
        service.correct(command(original, ApplicationFormChanges(admissionType = "MEISTER", region = "NATIONAL", addressBase = "수정 주소")), "operator-10")
        assertEquals("31007", persistence.findById(original.id)!!.examineeNumber)
        assertEquals("31007", screening.findById(original.id).get().examineeNumber)
        assertEquals(Region.NATIONAL, persistence.findById(original.id)!!.region)
        assertEquals("수정 주소", persistence.findById(original.id)!!.addressBase)
        assertEquals(110.0, persistence.findById(original.id)!!.totalScore!!, 0.0)
        val next = applicants.saveAndFlush(ApplicantJpaEntity.from(original.copy(
            id = 0, accountId = 20, examineeNumber = null,
        ))).toDomain()
        screening.saveAndFlush(ScreeningJpaEntity(applicantId = next.id, isArrived = true))
        val management = hs.kr.entrydsm.application.administration.application.ApplicantService(
            administration, administration, administration, DistancePort { 0 }, java.time.Clock.systemUTC())
        val stale = administration.findById(next.id)!!
        management.issueAll()
        assertEquals("31008", persistence.findById(next.id)!!.examineeNumber)
        assertEquals("31007", persistence.findById(original.id)!!.examineeNumber)
        assertEquals("31007", screening.findById(original.id).get().examineeNumber)
        assertError(ErrorCode.APPLICATION_VERSION_CONFLICT) { administration.save(stale.copy(examineeNumber = "31009")) }
        assertThrows(IllegalArgumentException::class.java) { applicationApi.updateExamineeNumber(original.id, "12001") }
        assertEquals(4L, persistence.findById(next.id)!!.statusVersion)
        assertEquals(2L, outbox.count())
        management.issueAll()
        assertEquals(2L, outbox.count())
    }

    private fun assertError(code: ErrorCode, action: () -> Unit) =
        assertEquals(code, assertThrows(AdminDomainException::class.java, action).errorCode)

    private fun command(original: Applicant, changes: ApplicationFormChanges) =
        CorrectApplicationCommand(original.id, "입력 오류 정정", changes)

    private fun fixture(): Applicant = applicants.saveAndFlush(ApplicantJpaEntity.from(Applicant(
        id = 0, accountId = 10, admissionType = AdmissionType.REGULAR, region = Region.DAEJEON,
        graduationType = GraduationType.GED, academicRecord = AcademicRecord(gedScores = GedScores(100, 100, 100, 100, 100, 100, 100)),
        photoFileId = "photo_test", name = "테스트지원자", phoneNumber = "010-1234-5678", gender = Gender.MALE,
        birthdate = LocalDate.of(2010, 1, 1), guardianName = "테스트보호자", guardianPhoneNumber = "010-9876-5432",
        guardianGender = Gender.FEMALE, guardianRelation = "모", zipCode = "12345", addressBase = "기존 주소", addressDetail = "상세 주소",
        introduction = "기존 자기소개서", studyPlan = "기존 학업계획서", status = ApplicantStatus.SUBMITTED,
        submittedAt = LocalDateTime.of(2026, 10, 1, 0, 0), totalScore = 170.0, statusVersion = 3,
    ))).toDomain()

    class FailingWrites(
        private val audits: ApplicationCorrectionAdapter,
        private val outbox: ApplicantStatusOutboxAdapter,
    ) : ApplicationCorrectionAuditRepository, ApplicantStatusEventOutbox {
        var failAudit = false
        var failOutbox = false
        var lastEvent: ApplicantStatusChanged? = null
        override fun add(audit: ApplicationCorrectionAudit) { audits.add(audit); if (failAudit) error("감사 기록 실패 테스트") }
        override fun add(event: ApplicantStatusChanged) { lastEvent = event; outbox.add(event); if (failOutbox) error("outbox 실패 테스트") }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = [ApplicantJpaEntity::class, ScreeningJpaEntity::class, ApplicationCorrectionAuditJpaEntity::class])
    @EnableJpaRepositories(basePackageClasses = [ApplicantJpaRepository::class, ScreeningJpaRepository::class, ApplicationCorrectionAuditJpaRepository::class])
    @Import(ApplicantPersistenceAdapter::class, ApplicationCorrectionService::class,
        MiddleSchoolPersistenceAdapter::class, LocalApplicantDataAdapter::class, ScreeningResultEventHandler::class)
    class Config {
        @Bean fun states(screening: ScreeningJpaRepository, audits: ApplicationCorrectionAuditJpaRepository) =
            ApplicationCorrectionAdapter(screening, audits)
        @Bean @org.springframework.context.annotation.Primary
        fun writes(adapter: ApplicationCorrectionAdapter, repository: ApplicantStatusOutboxJpaRepository) =
            FailingWrites(adapter, ApplicantStatusOutboxAdapter(repository,
                SnapshotCipher("test", mapOf("test" to "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="))))
        @Bean fun phoneValidator() = AccountPhoneValidator { _, _ -> true }
        @Bean fun application(repository: ApplicantRepository, writes: FailingWrites): ApplicationPort =
            ApplicationCommandService(repository, ApplicationPeriodReader { null }, phoneValidator(), writes)
    }
}
