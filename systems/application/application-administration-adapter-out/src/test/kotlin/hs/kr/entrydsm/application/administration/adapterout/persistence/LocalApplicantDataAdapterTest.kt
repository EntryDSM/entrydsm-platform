package hs.kr.entrydsm.application.administration.adapterout.persistence

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.application.administration.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.application.adapterout.repository.ApplicantJpaRepository
import hs.kr.entrydsm.application.adapterout.repository.ScreeningResultEventHandler
import hs.kr.entrydsm.application.application.exception.EvaluationValidationException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import java.lang.reflect.Proxy
import java.util.Optional
import org.junit.Assert.*
import org.junit.Test

class LocalApplicantDataAdapterTest {
    @Test
    fun absentApplicantReturnsNullWithoutReadingForm() {
        val adapter = adapter(null) { error("없는 지원자의 성적을 조회하면 안 된다") }
        assertNull(adapter.findDetailById(5))
        assertNull(adapter.findDetailById(0))
    }

    @Test
    fun originalApplicantIsReturnedWithoutProjection() {
        val adapter = adapter(applicant()) { form() }
        assertEquals(5L, adapter.findDetailById(5)?.applicant?.id)
        assertEquals(listOf(5L), adapter.findAll(ApplicantFilter()).map { it.id })
        assertEquals("0005", adapter.findAdmissionFileRows().single().receiptNumber)
    }

    @Test
    fun gedScoresAreIncludedOnlyForGedApplicants() {
        val scores = hs.kr.entrydsm.application.domain.model.GedScores(
            koreanScore = 0, societyScore = 81, historyScore = 82, mathScore = 83,
            scienceScore = 84, technologyScore = 85, englishScore = 100,
        )
        val gedForm = form().copy(
            graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GED,
            gedScores = scores,
        )
        val row = adapter(applicant()) { gedForm }.findAdmissionFileRows().single()
        assertEquals(hs.kr.entrydsm.admin.domain.model.GedScores(0, 81, 82, 83, 84, 85, 100), row.gedScores)
        assertNull(adapter(applicant()) {
            gedForm.copy(graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GRADUATED)
        }.findAdmissionFileRows().single().gedScores)
    }

    @Test
    fun scoreFailureRemainsFailureAndNeverBecomesMissingApplicant() {
        val adapter = adapter(applicant()) { throw EvaluationValidationException("성적 누락") }
        val error = assertThrows(AdminDomainException::class.java) { adapter.findDetailById(5) }
        assertEquals(ErrorCode.APPLICATION_SCORE_INVALID, error.errorCode)
        assertEquals(502, error.errorCode.status)
        assertEquals(listOf(5L), error.targetIds)
        val exportError = assertThrows(AdminDomainException::class.java) { adapter.findAdmissionFileRows() }
        assertEquals(1, exportError.failedCount)
        assertEquals(1, exportError.totalCount)
        assertEquals(listOf(5L), exportError.targetIds)
    }

    @Test
    fun missingOrMismatchedFormFailsInsteadOfExportingPartialData() {
        assertEquals(ErrorCode.APPLICATION_SCORE_INVALID,
            assertThrows(AdminDomainException::class.java) {
                adapter(applicant()) { form().copy(score = null) }.findDetailById(5)
            }.errorCode)
        assertEquals(ErrorCode.APPLICATION_FORM_NOT_FOUND,
            assertThrows(AdminDomainException::class.java) {
                adapter(applicant()) { null }.findDetailById(5)
            }.errorCode)
        assertEquals(ErrorCode.APPLICATION_FORM_INVALID,
            assertThrows(AdminDomainException::class.java) {
                adapter(applicant()) { form().copy(applicantId = 6) }.findDetailById(5)
            }.errorCode)
    }

    private fun adapter(applicant: ApplicantResult?, readForm: () -> ApplicationFormResult?): LocalApplicantDataAdapter {
        val application = proxy(ApplicationPort::class.java) { method -> when (method) {
            "findApplicant" -> applicant
            "listApplicants" -> listOfNotNull(applicant)
            "findApplicationForm" -> readForm()
            else -> error("예상하지 않은 호출: $method")
        } }
        val screenings = proxy(ScreeningJpaRepository::class.java) { method -> when (method) {
            "findAll" -> emptyList<Any>()
            "findById" -> Optional.empty<Any>()
            else -> error("예상하지 않은 호출: $method")
        } }
        val results = ScreeningResultEventHandler(
            proxy(ApplicantJpaRepository::class.java) { error("조회 중 원서를 변경하면 안 된다") },
            ApplicantStatusEventOutbox { error("조회 중 이벤트를 작성하면 안 된다") }, application,
        )
        return LocalApplicantDataAdapter(application, screenings, results,
            proxy(ApplicantJpaRepository::class.java) { error("조회 중 잠금을 잡으면 안 된다") })
    }

    private fun applicant() = ApplicantResult(
        applicantId = 5, accountId = 10, name = null, schoolName = null, region = null,
        admissionType = null, photoFileId = null, birthdate = null, phoneNumber = null,
        graduationType = null, totalScore = null, status = ApplicantStatus.SUBMITTED, submittedAt = null,
    )

    private fun form() = ApplicationFormResult(
        applicantId = 5, accountId = 10, status = ApplicantStatus.SUBMITTED, name = null,
        phoneNumber = null, birthdate = null, gender = null, address = null, photoFileId = null,
        region = null, admissionType = null, specialAdmissionType = SpecialAdmissionType.NONE,
        graduationType = null, graduationDate = null, guardianName = null, guardianRelation = null,
        guardianPhoneNumber = null, middleSchool = null, thirdGradeSecondSemester = null,
        thirdGradeFirstSemester = null, previousSemester = null, secondPreviousSemester = null,
        academicRecord = null, score = hs.kr.entrydsm.application.domain.service.ScoreBreakdown(1.0, 2.0, 3.0, 0.0, 6.0), introduction = null, studyPlan = null,
    )

    private fun <T> proxy(type: Class<T>, invoke: (String) -> Any?): T =
        type.cast(Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> invoke(method.name) })
}
