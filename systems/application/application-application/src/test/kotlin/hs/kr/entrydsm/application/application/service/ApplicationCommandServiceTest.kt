package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.exception.SensitiveConsentRequiredException
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import java.lang.reflect.Modifier
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
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
        val service = ApplicationCommandService(repository) { event = it }

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
        val service = ApplicationCommandService(repository)

        service.submit(accountId = 10L)
        assertEquals(ApplicantStatus.SUBMITTED, repository.savedApplicant?.status)
        assertNotNull(repository.savedApplicant?.submittedAt)

        val canceled = service.cancel(10L, "개인 사유")
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", repository.savedApplicant?.cancelReason)
    }

    @Test
    fun cancelRejectsDraftApplication() {
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)))

        assertThrows(ApplicationCancelNotAllowedException::class.java) {
            service.cancel(10L, null)
        }
    }

    @Test
    fun updateTypeClearsMiddleSchoolInfoAndSubjectGradesWhenChangedToGed() {
        val repository = FakeApplicantRepository(
            Applicant(
                id = 1L,
                accountId = 10L,
                graduationType = GraduationType.PROSPECTIVE,
                middleSchoolInfo = MiddleSchoolInfo(
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
        val service = ApplicationCommandService(repository)

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
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)))
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
        val service = ApplicationCommandService(repository)

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
        val service = ApplicationCommandService(repository)

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

            ApplicationCommandService(repository).submit(accountId = 10L)

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
        private var applicant: Applicant,
    ) : ApplicantRepository {
        var savedApplicant: Applicant? = null

        override fun save(applicant: Applicant): Applicant {
            savedApplicant = applicant
            this.applicant = applicant
            return applicant
        }

        // 목록은 쿼리가 거른다. ApplicantSummaryQueryTest 가 덮는다.
        override fun findSummariesByStatusIn(statuses: Set<ApplicantStatus>): List<ApplicantResult> =
            emptyList()

        override fun findById(id: Long): Applicant? =
            applicant.takeIf { it.id == id }

        override fun findByAccountId(accountId: Long): Applicant? =
            applicant.takeIf { it.accountId == accountId }
    }

    private companion object {
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
