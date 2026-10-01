package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.adapterout.entity.InstitutionCodeJpaEntity
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.application.grpc.ScreeningResultChangedEvent
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.connection.stream.RecordId
import java.lang.reflect.Proxy
import java.util.Base64
import jakarta.persistence.EntityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

/**
 * 원서 서식이 읽는 지원자 전문 조회입니다.
 *
 * 출신지역에 찍을 중학교 소재지는 원서에 저장하지 않고 기관코드 표에서 읽습니다.
 * 영속성 컨텍스트를 비우고 읽어야 기관코드가 지연 프록시로 오므로, 프록시를 거쳐도 주소가 차는지 봅니다.
 */
@RunWith(SpringRunner::class)
@DataJpaTest(properties = ["security.pii.encryption-key-base64=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="])
@ContextConfiguration(classes = [ApplicantPersistenceAdapterTest.JpaTestConfig::class])
class ApplicantPersistenceAdapterTest {

    @Autowired
    private lateinit var applicantJpaRepository: ApplicantJpaRepository

    @Autowired
    private lateinit var institutionCodeJpaRepository: InstitutionCodeJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var statusOutboxRepository: ApplicantStatusOutboxJpaRepository

    private fun applicationPort() = ApplicationCommandService(ApplicantPersistenceAdapter(applicantJpaRepository),
        ApplicationPeriodReader { null }, AccountPhoneValidator { _, _ -> true })

    @Test
    fun `동시에 변경된 원서의 오래된 쓰기는 거절한다`() {
        val saved = applicantJpaRepository.saveAndFlush(ApplicantJpaEntity(accountId = 505))
        entityManager.createNativeQuery("UPDATE applicants SET lock_version = lock_version + 1 WHERE id = :id")
            .setParameter("id", saved.id).executeUpdate()
        saved.name = "동시 수정"
        assertThrows(jakarta.persistence.OptimisticLockException::class.java) { entityManager.flush() }
    }

    @Test
    fun `최종 결과와 identity 전달 이벤트를 함께 영속화한다`() {
        val applicant = applicantJpaRepository.saveAndFlush(ApplicantJpaEntity(accountId = 504, status = ApplicantStatus.SUBMITTED))
        val cipher = SnapshotCipher("test", mapOf("test" to Base64.getEncoder().encodeToString(ByteArray(32) { 7 })))
        val handler = ScreeningResultEventHandler(applicantJpaRepository, ApplicantStatusOutboxAdapter(statusOutboxRepository, cipher), applicationPort())
        handler.consume(ScreeningResultChangedEvent.newBuilder().setApplicantId(requireNotNull(applicant.id)).setVersion(10)
            .setPassStatus(PassStatus.PASS_STATUS_FINAL_PASSED).setOccurredAtEpochMillis(1000).build())
        entityManager.flush()
        entityManager.clear()
        val event = ApplicantStatusChangedEvent.parseFrom(statusOutboxRepository.findAll().single().payload)
        assertEquals(PassStatus.PASS_STATUS_FINAL_PASSED, event.passStatus)
        assertEquals(504L, event.accountId)
        assertEquals(1000L, event.announcedAtEpochMillis)
        assertEquals(1L, event.version)
        val form = ApplicationFormResponse.parseFrom(cipher.decrypt(event.encryptedApplicationForm.toByteArray()))
        assertEquals(event.applicantId, form.applicantId)
        assertEquals(event.version, form.statusVersion)
        assertEquals(ResultType.FINAL, applicantJpaRepository.findById(requireNotNull(applicant.id)).get().toDomain().passResultType)
    }

    @Test
    fun `수신 실패 이벤트는 ack하지 않고 뒤의 정상 이벤트를 처리한다`() {
        val applicant = applicantJpaRepository.saveAndFlush(ApplicantJpaEntity(accountId = 503, status = ApplicantStatus.SUBMITTED))
        val event = ScreeningResultChangedEvent.newBuilder().setApplicantId(requireNotNull(applicant.id)).setVersion(1)
            .setPassStatus(PassStatus.PASS_STATUS_FINAL_PASSED).setOccurredAtEpochMillis(1000).build()
        val records = listOf("invalid-base64", Base64.getEncoder().encodeToString(event.toByteArray())).mapIndexed { index, payload ->
            StreamRecords.newRecord().`in`("test").ofMap(mapOf("payload" to payload)).withId(RecordId.of("1-$index"))
        }
        val acknowledged = mutableListOf<Any?>()
        val events = mutableListOf<ApplicantStatusChanged>()
        val redis = object : StringRedisTemplate() {
            override fun <T : Any?> execute(action: RedisCallback<T>): T? = null
            override fun <HK : Any, HV : Any> opsForStream(): StreamOperations<String, HK, HV> =
                Proxy.newProxyInstance(javaClass.classLoader, arrayOf(StreamOperations::class.java)) { _, method, args ->
                    when (method.name) {
                        "read" -> records
                        "acknowledge" -> { acknowledged.addAll((args[2] as Array<*>).toList()); 1L }
                        else -> error("예상하지 못한 호출: ${method.name}")
                    }
                } as StreamOperations<String, HK, HV>
        }
        val handler = ScreeningResultEventHandler(applicantJpaRepository, ApplicantStatusEventOutbox { events.add(it) }, applicationPort())
        val consumer = ScreeningResultRedisConsumer(redis, handler, "test", "application", "application")
        consumer.poll()
        consumer.poll()
        assertEquals(1, events.size)
        assertTrue(acknowledged.isNotEmpty())
        assertTrue(acknowledged.all { it == RecordId.of("1-1") })
    }

    @Test
    fun `관리자 결과를 영속화하고 학생 이벤트로 전달하며 중복 역순 정정을 처리한다`() {
        val saved = applicantJpaRepository.saveAndFlush(ApplicantJpaEntity(accountId = 501, status = ApplicantStatus.SUBMITTED))
        val id = requireNotNull(saved.id)
        val events = mutableListOf<ApplicantStatusChanged>()
        val handler = ScreeningResultEventHandler(applicantJpaRepository, ApplicantStatusEventOutbox { events.add(it) }, applicationPort())
        fun consume(version: Long, status: PassStatus) {
            handler.consume(ScreeningResultChangedEvent.newBuilder().setApplicantId(id).setVersion(version)
                .setPassStatus(status).setOccurredAtEpochMillis(1000).build())
            entityManager.flush()
            entityManager.clear()
        }
        consume(1, PassStatus.PASS_STATUS_FIRST_PASSED)
        assertEquals(ResultType.DOCUMENT, applicantJpaRepository.findById(id).get().toDomain().passResultType)
        consume(2, PassStatus.PASS_STATUS_FINAL_PASSED)
        assertEquals(ResultType.FINAL, applicantJpaRepository.findById(id).get().toDomain().passResultType)
        assertEquals(PassResultStatus.PASS, events.last().passStatus)
        consume(2, PassStatus.PASS_STATUS_FINAL_PASSED)
        consume(1, PassStatus.PASS_STATUS_FIRST_FAILED)
        assertEquals(2, events.size)
        consume(3, PassStatus.PASS_STATUS_FIRST_FAILED)
        assertEquals(ResultType.DOCUMENT, events.last().passResultType)
        assertEquals(PassResultStatus.FAIL, events.last().passStatus)
        consume(4, PassStatus.PASS_STATUS_NOT_ANNOUNCED)
        assertEquals(PassResultStatus.PENDING, events.last().passStatus)
        assertEquals(0, applicantJpaRepository.findById(id).get().passResults.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), events.map { it.version })
        assertEquals(501L, events.last().accountId)
    }

    @Test
    fun `취소되거나 삭제된 지원자에게 지연 결과를 반영하지 않는다`() {
        val saved = applicantJpaRepository.saveAndFlush(ApplicantJpaEntity(accountId = 502, status = ApplicantStatus.CANCELED))
        val events = mutableListOf<ApplicantStatusChanged>()
        val handler = ScreeningResultEventHandler(applicantJpaRepository, ApplicantStatusEventOutbox { events.add(it) }, applicationPort())
        for (id in listOf(requireNotNull(saved.id), Long.MAX_VALUE)) {
            handler.consume(ScreeningResultChangedEvent.newBuilder().setApplicantId(id).setVersion(1)
                .setPassStatus(PassStatus.PASS_STATUS_FINAL_PASSED).build())
        }
        assertTrue(events.isEmpty())
        assertTrue(saved.passResults.isEmpty())
    }

    @Test
    fun `개인정보는 암호화해서 저장하고 평문으로 조회한다`() {
        val applicant = Applicant(
            id = 0,
            accountId = 100,
            name = "홍길동",
            phoneNumber = "01012345678",
            guardianName = "홍보호",
            guardianPhoneNumber = "01087654321",
            addressBase = "대전광역시 유성구",
            addressDetail = "101동 101호",
            zipCode = "34111",
        )
        applicantJpaRepository.saveAndFlush(ApplicantJpaEntity.from(applicant))
        entityManager.clear()

        val stored = entityManager.createNativeQuery(
            "select name, phone_number, guardian_name, guardian_phone_number, address_base, address_detail, zip_code " +
                "from applicants where account_id = 100",
        ).singleResult as Array<*>
        listOf(
            applicant.name,
            applicant.phoneNumber,
            applicant.guardianName,
            applicant.guardianPhoneNumber,
            applicant.addressBase,
            applicant.addressDetail,
            applicant.zipCode,
        ).zip(stored).forEach { (plain, encrypted) -> assertNotEquals(plain, encrypted) }

        assertEquals(applicant.name, applicantJpaRepository.findByAccountId(100)?.name)
        assertEquals(applicant.addressDetail, applicantJpaRepository.findByAccountId(100)?.addressDetail)
    }

    @Test
    fun `중학교 소재지를 기관코드 표에서 채운다`() {
        institutionCodeJpaRepository.save(
            InstitutionCodeJpaEntity(
                code = SCHOOL_CODE,
                fullName = "대전광역시교육청 대전서부교육지원청 대덕중학교",
                name = "대덕중학교",
                postalCode = "34111",
                address = SCHOOL_ADDRESS,
                phoneNumber = null,
                faxNumber = null,
            ),
        )
        applicantJpaRepository.save(
            ApplicantJpaEntity.from(
                Applicant(
                    id = 0,
                    accountId = 101,
                    middleSchoolInfo = MiddleSchoolInfo(
                        schoolCode = SCHOOL_CODE,
                        schoolName = "대덕중학교",
                        studentNumber = "30101",
                        schoolPhone = "0421234567",
                        teacherName = "홍길동",
                    ),
                ),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val middleSchool = ApplicantPersistenceAdapter(applicantJpaRepository)
            .findByAccountId(101)?.middleSchoolInfo

        assertEquals(SCHOOL_CODE, middleSchool?.schoolCode)
        assertEquals(SCHOOL_ADDRESS, middleSchool?.schoolAddress)
    }

    @Test
    fun `원서와 연관 데이터를 함께 삭제한다`() {
        institutionCodeJpaRepository.save(
            InstitutionCodeJpaEntity(
                code = SCHOOL_CODE,
                fullName = "대덕중학교",
                name = "대덕중학교",
                postalCode = null,
                address = null,
                phoneNumber = null,
                faxNumber = null,
            ),
        )
        val saved = applicantJpaRepository.saveAndFlush(
            ApplicantJpaEntity.from(
                Applicant(
                    id = 0,
                    accountId = 102,
                    middleSchoolInfo = MiddleSchoolInfo(SCHOOL_CODE, "중학교", "30101", "0421234567", "담임"),
                ),
            ),
        )

        ApplicantPersistenceAdapter(applicantJpaRepository).deleteById(requireNotNull(saved.id))
        entityManager.flush()

        assertFalse(applicantJpaRepository.existsById(requireNotNull(saved.id)))
    }

    @SpringBootConfiguration
    @EnableJpaRepositories(basePackageClasses = [ApplicantJpaRepository::class])
    @EntityScan(basePackageClasses = [ApplicantJpaEntity::class])
    class JpaTestConfig

    private companion object {
        const val SCHOOL_CODE = "7031234"
        const val SCHOOL_ADDRESS = "대전광역시 유성구 가정북로 76"
    }
}
