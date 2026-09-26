package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationPeriodClosedException
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.exception.SensitiveConsentRequiredException
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateApplicantArrivalCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import java.lang.reflect.Modifier
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationCommandServiceTest {
    @Test
    fun transactionalServiceCanBeProxied() {
        assertFalse(Modifier.isFinal(ApplicationCommandService::class.java.modifiers))
    }

    @Test
    fun createReturnsExistingApplicantWithoutSavingAndAllowsNewAccount() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        var event: ApplicantStatusChanged? = null
        val service = ApplicationCommandService(repository, OPEN) { event = it }

        val existing = service.createApplicant(CreateApplicantCommand(10L))
        assertEquals(1L, existing.applicantId)
        assertFalse(existing.created)
        assertNull(repository.savedApplicant)
        assertNull(event)

        val created = service.createApplicant(CreateApplicantCommand(11L))
        assertTrue(created.created)
        assertEquals(11L, repository.savedApplicant?.accountId)
        assertEquals(11L, event?.accountId)
        assertEquals(ApplicantStatus.DRAFT, event?.status)
    }

    @Test
    fun submitAndCancelPersistLifecycle() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                admissionType = AdmissionType.REGULAR,
                name = "홍길동",
                guardianName = "보호자",
                introduction = "소개",
                studyPlan = "학업 계획",
            ),
        )
        val service = ApplicationCommandService(repository, OPEN)

        service.submit(accountId = 10L)
        assertEquals(ApplicantStatus.SUBMITTED, repository.savedApplicant?.status)
        assertNotNull(repository.savedApplicant?.submittedAt)

        val canceled = service.cancel(10L, "개인 사유")
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", repository.savedApplicant?.cancelReason)
    }

    @Test
    fun cancelRejectsDraftApplication() {
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)), OPEN)

        assertThrows(ApplicationCancelNotAllowedException::class.java) {
            service.cancel(10L, null)
        }
    }

    @Test
    fun arrivalChangePersistsAndPublishesOnlyWhenValueChanges() {
        val repository = FakeApplicantRepository(
            Applicant(id = 1L, accountId = 10L, status = ApplicantStatus.SUBMITTED, statusVersion = 2),
        )
        val events = mutableListOf<ApplicantStatusChanged>()
        val service = ApplicationCommandService(repository, OPEN, events::add)

        val arrived = service.updateArrival(UpdateApplicantArrivalCommand(1L, true))
        assertEquals(ApplicantStatus.ARRIVAL, arrived.applicantStatus)
        assertEquals(1L, events.single().applicantId)
        assertEquals(3L, events.single().version)

        service.updateArrival(UpdateApplicantArrivalCommand(1L, true))
        assertEquals(1, events.size)

        val canceled = service.updateArrival(UpdateApplicantArrivalCommand(1L, false))
        assertEquals(ApplicantStatus.SUBMITTED, canceled.applicantStatus)
        assertEquals(4L, events.last().version)
    }

    @Test
    fun issuedExamineeNumberIsSavedAndReturnedInForm() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        val service = ApplicationCommandService(repository, OPEN)

        service.updateExamineeNumber(1L, "11001")

        assertEquals("11001", repository.savedApplicant?.examineeNumber)
        assertEquals("11001", service.findApplicationForm(10L)?.examineeNumber)
    }

    @Test
    fun outsideApplicationPeriodRejectsWritingButStillReturnsExistingApplicant() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                admissionType = AdmissionType.REGULAR,
                name = "홍길동",
                guardianName = "보호자",
                introduction = "소개",
                studyPlan = "학업 계획",
            ),
        )
        val events = mutableListOf<ApplicantStatusChanged>()
        val service = ApplicationCommandService(repository, CLOSED, events::add)

        // applicantId 를 받는 유일한 경로라 기간이 끝나도 이미 있는 원서는 돌려준다.
        assertEquals(1L, service.createApplicant(CreateApplicantCommand(10L)).applicantId)
        assertThrows(ApplicationPeriodClosedException::class.java) {
            service.createApplicant(CreateApplicantCommand(11L))
        }
        assertThrows(ApplicationPeriodClosedException::class.java) {
            service.updateIntroduction(accountId = 10L, introduction = "고친 소개")
        }
        assertThrows(ApplicationPeriodClosedException::class.java) { service.submit(accountId = 10L) }

        assertNull(repository.savedApplicant)
        assertTrue(events.isEmpty())
    }

    @Test
    fun cancelAndArrivalDoNotDependOnApplicationPeriod() {
        fun submitted() = FakeApplicantRepository(
            Applicant(id = 1L, accountId = 10L, status = ApplicantStatus.SUBMITTED, statusVersion = 2),
        )

        val canceled = ApplicationCommandService(submitted(), CLOSED).cancel(10L, null)
        val arrived = ApplicationCommandService(submitted(), CLOSED).updateArrival(UpdateApplicantArrivalCommand(1L, true))

        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals(ApplicantStatus.ARRIVAL, arrived.applicantStatus)
    }

    @Test
    fun deletePublishesDeletionAndAllowsSameAccountToCreateAgain() {
        val repository = FakeApplicantRepository(
            Applicant(id = 3L, accountId = 10L, status = ApplicantStatus.COMPLETED, statusVersion = 7),
        )
        val events = mutableListOf<ApplicantStatusChanged>()
        val service = ApplicationCommandService(repository, OPEN, events::add)

        service.deleteApplicant(3L)

        assertEquals(3L, repository.deletedId)
        assertTrue(events.single().deleted)
        assertEquals(8L, events.single().version)
        assertEquals(3L, events.single().applicantId)
        assertTrue(service.createApplicant(CreateApplicantCommand(10L)).created)
    }

    @Test
    fun deleteRejectsMissingApplicantWithoutPublishing() {
        val events = mutableListOf<ApplicantStatusChanged>()
        val service = ApplicationCommandService(
            FakeApplicantRepository(Applicant(id = 3L, accountId = 10L)),
            CLOSED,
            events::add,
        )

        assertThrows(ApplicantNotFoundException::class.java) { service.deleteApplicant(404L) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun updateTypeClearsMiddleSchoolInfoAndSubjectGradesWhenChangedToGed() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                graduationType = GraduationType.PROSPECTIVE,
                middleSchoolInfo = MiddleSchoolInfo(
                    schoolCode = "D100000",
                    schoolName = "대덕중학교",
                    studentNumber = "30101",
                    schoolPhone = "042-000-0000",
                    teacherName = "담임",
                ),
                academicRecord = AcademicRecord(
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                    ),
                ),
            ),
        )
        val service = ApplicationCommandService(repository, OPEN)

        service.updateType(
            accountId = 10L,
            admissionType = AdmissionType.REGULAR,
            region = Region.DAEJEON,
            graduationType = GraduationType.GED,
            graduationDate = null,
        )

        val savedApplicant = requireNotNull(repository.savedApplicant)
        assertNull(savedApplicant.middleSchoolInfo)
        assertTrue(savedApplicant.academicRecord?.subjectGrades?.isEmpty() == true)
    }

    @Test
    fun socialAdmissionRequiresSensitiveConsent() {
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)), OPEN)
        val command = UpdateTypeCommand(
            accountId = 10L,
            admissionType = AdmissionType.SOCIAL,
            region = Region.DAEJEON,
            graduationType = GraduationType.GED,
            graduationDate = null,
            isSensitiveAgree = false,
        )

        assertThrows(SensitiveConsentRequiredException::class.java) { service.updateType(command) }
    }

    @Test
    fun updateFindsApplicantByRequesterAccount() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        val service = ApplicationCommandService(repository, OPEN)

        service.updateIntroduction(accountId = 10L, introduction = "자기소개")
        assertEquals("자기소개", repository.savedApplicant?.introduction)

        assertThrows(ApplicantNotFoundException::class.java) {
            service.updateIntroduction(accountId = 11L, introduction = "남의 원서")
        }
    }

    @Test
    fun applicationFormSkipsFreeSemesterWhenPickingPreviousColumns() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                zipCode = "34503",
                addressBase = "대전광역시 유성구 가정북로 76",
                addressDetail = "101동 1001호",
                academicRecord = AcademicRecord(
                    subjectGrades = linkedMapOf(
                        SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.A),
                        // 자유학기라 반영할 과목이 하나도 없다. 직전학기 후보에서 건너뛴다.
                        SchoolSemester.SECOND_GRADE_SECOND_SEMESTER to all(SubjectGrade.X),
                        SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to all(SubjectGrade.B),
                        SchoolSemester.FIRST_GRADE_SECOND_SEMESTER to all(SubjectGrade.C),
                        SchoolSemester.FIRST_GRADE_FIRST_SEMESTER to all(SubjectGrade.D),
                    ),
                ),
            ),
        )
        val service = ApplicationCommandService(repository, OPEN)

        val form = requireNotNull(service.findApplicationForm(10L))

        // 졸업예정자라 3학년 2학기 열은 비고, 직전·직전전은 자유학기를 건너뛴 상대 순서다.
        // ScoreCalculator 의 반영 학기 선택과 같은 순서여야 인쇄한 원서와 점수가 어긋나지 않는다.
        assertNull(form.thirdGradeSecondSemester)
        assertEquals(SubjectGrade.A, form.thirdGradeFirstSemester?.koreanGrade)
        assertEquals(SubjectGrade.B, form.previousSemester?.koreanGrade)
        assertEquals(SubjectGrade.C, form.secondPreviousSemester?.koreanGrade)
        // 열은 넷뿐이라 1학년 1학기(D)는 쓰이지 않는다.

        // 서식의 주소 칸은 우편번호까지 합친 한 줄이다.
        assertEquals("(34503) 대전광역시 유성구 가정북로 76 101동 1001호", form.address)

        // 원서는 계정으로 찾는다. 남의 계정으로는 나오지 않는다.
        assertNull(service.findApplicationForm(11L))
    }

    @Test
    fun batchApplicationFormsIncludeClassAndGedAverage() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                middleSchoolInfo = MiddleSchoolInfo("code", "중학교", "30215", "phone", "teacher"),
                academicRecord = AcademicRecord(
                    gedScores = GedScores(100, 90, 80, 70, 60, 50, 40),
                ),
            ),
        )

        val forms = ApplicationCommandService(repository, OPEN).findApplicationForms(listOf(10L, 11L))

        assertEquals(1, forms.size)
        assertEquals("2", forms.single().classNumber)
        assertEquals(70.0, forms.single().gedAverage)
    }

    @Test
    fun applicationFormCarriesGedScoresOnlyWhileGraduationTypeIsGed() {
        val scores = GedScores(100, 90, 80, 70, 60, 50, 40)
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                graduationType = GraduationType.GED,
                academicRecord = AcademicRecord(gedScores = scores),
            ),
        )
        val service = ApplicationCommandService(repository, OPEN)

        assertEquals(scores, service.findApplicationForm(10L)?.gedScores)

        // 졸업예정으로 바꿔도 학기 성적을 넣기 전까지 검정고시 점수가 남는다. 원서에는 싣지 않는다.
        service.updateType(10L, AdmissionType.REGULAR, Region.DAEJEON, GraduationType.PROSPECTIVE, YearMonth.of(2027, 2))
        assertNull(service.findApplicationForm(10L)?.gedScores)
    }

    /**
     * 시각을 시간대 없이 쓰면 UTC 로 도는 컨테이너에서만 맞습니다. gRPC 와 이벤트가 UTC 로
     * 되읽으므로, 기기 시간대가 무엇이든 저장하는 값은 UTC 여야 합니다. 어긋나면 통계의
     * 일자별 추이가 시차만큼 밀립니다.
     */
    @Test
    fun recordsTimestampsInUtcWhateverTheMachineZoneIs() {
        withDefaultTimeZone("Asia/Seoul") {
            val repository = FakeApplicantRepository(
                Applicant(
                    id = 1L,
                    accountId = 10L,
                    admissionType = AdmissionType.REGULAR,
                    name = "홍길동",
                    guardianName = "보호자",
                    introduction = "소개",
                    studyPlan = "학업 계획",
                ),
            )

            ApplicationCommandService(repository, OPEN).submit(accountId = 10L)

            val saved = repository.savedApplicant!!
            assertRecordedInUtc(saved.submittedAt!!)
            assertRecordedInUtc(saved.updatedAt)
        }
    }

    private fun assertRecordedInUtc(recorded: LocalDateTime) {
        val gap = Duration.between(recorded.toInstant(ZoneOffset.UTC), Instant.now()).abs()
        assertTrue(
            "UTC 로 읽으면 지금과 ${gap.toMinutes()}분 어긋난다: $recorded",
            gap < Duration.ofMinutes(1),
        )
    }

    private fun withDefaultTimeZone(zoneId: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    private class FakeApplicantRepository(
        private var applicant: Applicant?,
    ) : ApplicantRepository {
        var savedApplicant: Applicant? = null
        var deletedId: Long? = null

        override fun save(applicant: Applicant): Applicant {
            savedApplicant = applicant
            this.applicant = applicant
            return applicant
        }

        // 목록은 쿼리가 거른다. ApplicantSummaryQueryTest 가 덮는다.
        override fun findSummariesByStatusIn(statuses: Set<ApplicantStatus>): List<ApplicantResult> =
            emptyList()

        override fun findById(id: Long): Applicant? =
            applicant?.takeIf { it.id == id }

        override fun findByAccountId(accountId: Long): Applicant? =
            applicant?.takeIf { it.accountId == accountId }

        override fun deleteById(id: Long) {
            deletedId = id
            applicant = null
        }
    }

    private companion object {
        val OPEN = ApplicationPeriodReader { Instant.MIN..Instant.MAX }
        val CLOSED = ApplicationPeriodReader { null }

        fun all(grade: SubjectGrade): SubjectGrades =
            SubjectGrades(
                koreanGrade = grade,
                mathGrade = grade,
                englishGrade = grade,
                scienceGrade = grade,
                societyGrade = grade,
                technologyGrade = grade,
                historyGrade = grade,
            )
    }
}
