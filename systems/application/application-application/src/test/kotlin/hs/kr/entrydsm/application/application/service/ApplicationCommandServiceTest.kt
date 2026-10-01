package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.exception.ApplicantAlreadyExistsException
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
    fun personalPhoneValidationRunsBeforeChangingOrSavingApplicant() {
        for (valid in listOf(true, false)) {
            val applicant = Applicant(id = 1L, accountId = 10L)
            val repository = FakeApplicantRepository(applicant)
            val validator = hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator { id, phone ->
                assertEquals(10L, id)
                assertEquals("010-1234-5678", phone)
                valid
            }
            val service = ApplicationCommandService(repository, OPEN, validator)
            val update = {
                service.updatePersonal(10L, "photo", "이름", "010-1234-5678", hs.kr.entrydsm.application.domain.enum.Gender.MALE,
                    java.time.LocalDate.of(2010, 1, 1), hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType.NONE)
            }
            if (valid) {
                update()
                assertEquals("010-1234-5678", repository.savedApplicant?.phoneNumber)
            } else {
                val failure = assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) { update() }
                assertEquals("APPLICATION_PHONE_NUMBER_MISMATCH", failure.errorCode.name)
                assertEquals("가입한 전화번호와 입력한 전화번호가 일치하지 않습니다.", failure.message)
                assertNull(repository.savedApplicant)
                assertNull(applicant.photoFileId)
                assertNull(applicant.phoneNumber)
            }
        }
    }

    @Test
    fun studentNumberMustHaveFiveDigitsAndValidGradeClassAndNumber() {
        val cases = listOf(
            "10314" to null, "30401" to null, "30101" to null,
            "10101" to null, "20199" to null, "39901" to null, "39999" to null,
            "54441" to "OUT_OF_RANGE", "00314" to "OUT_OF_RANGE",
            "30000" to "OUT_OF_RANGE", "30001" to "OUT_OF_RANGE", "30100" to "OUT_OF_RANGE",
            "1031" to "INVALID_FORMAT", "103140" to "INVALID_FORMAT",
            "10가14" to "INVALID_FORMAT", "１０３１４" to "INVALID_FORMAT",
        )
        for ((number, reason) in cases) {
            val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
            val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)
            val update = { service.updateMiddleSchool(10L, "code", "학교", number, "042-123-4567", "교사") }
            if (reason == null) {
                update()
                assertEquals(number, repository.savedApplicant?.middleSchoolInfo?.studentNumber)
            } else {
                val exception = assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) { update() }
                assertEquals("APPLICATION_STUDENT_NUMBER_$reason", exception.errorCode.name)
                if (reason == "OUT_OF_RANGE") assertEquals("중학교 학년은 1~3, 반과 번호는 각각 01~99 사이로 입력해주세요.", exception.message)
                assertNull(repository.savedApplicant)
                assertNull(repository.findByAccountId(10L)?.middleSchoolInfo)
            }
        }
    }

    @Test
    fun graduationYearMustBe2026Or2027() {
        for (year in listOf(2025, 2026, 2027, 2028)) {
            val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
            val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)
            if (year in 2026..2027) {
                service.updateType(10L, AdmissionType.REGULAR, Region.DAEJEON, GraduationType.PROSPECTIVE, YearMonth.of(year, 2))
                assertEquals(year, repository.savedApplicant?.graduationDate?.year)
            } else {
                val exception = assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) {
                    service.updateType(10L, AdmissionType.REGULAR, Region.DAEJEON, GraduationType.PROSPECTIVE, YearMonth.of(year, 2))
                }
                assertEquals("APPLICATION_GRADUATION_DATE_OUT_OF_RANGE", exception.errorCode.name)
                assertNull(repository.savedApplicant)
            }
        }
    }

    @Test
    fun sharedValidationIdentifiesMissingFieldsAndDoesNotSave() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)
        val cases = listOf<Pair<String, () -> Unit>>(
            "INTRODUCTION_REQUIRED" to { service.updateIntroduction(10L, "") },
            "INTRODUCTION_TOO_LONG" to { service.updateIntroduction(10L, "가".repeat(1601)) },
            "STUDY_PLAN_REQUIRED" to { service.updateStudyPlan(10L, "") },
            "GRADUATION_DATE_REQUIRED" to { service.updateType(10L, AdmissionType.REGULAR, Region.DAEJEON, GraduationType.PROSPECTIVE, null) },
            "GRADUATION_DATE_NOT_ALLOWED" to { service.updateType(10L, AdmissionType.REGULAR, Region.DAEJEON, GraduationType.GED, YearMonth.of(2027, 2)) },
            "ADMISSION_TYPE_REQUIRED" to { service.submit(10L) },
        )
        for ((code, operation) in cases) {
            val exception = assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) { operation() }
            assertEquals("APPLICATION_$code", exception.errorCode.name)
            assertNull(repository.savedApplicant)
        }
        val submitted = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 2L, accountId = 10L, status = ApplicantStatus.SUBMITTED)), OPEN, ACCEPT_PHONE)
        assertEquals("APPLICATION_NOT_EDITABLE", assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) {
            submitted.updateIntroduction(10L, "자기소개")
        }.errorCode.name)
    }

    @Test
    fun transactionalServiceCanBeProxied() {
        assertFalse(Modifier.isFinal(ApplicationCommandService::class.java.modifiers))
    }

    @Test
    fun createReturnsExistingApplicantWithoutSavingAndAllowsNewAccount() {
        val repository = FakeApplicantRepository(Applicant(id = 1L, accountId = 10L))
        var event: ApplicantStatusChanged? = null
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE) { event = it }

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
    fun createRejectsSubmittedApplicant() {
        val repository = FakeApplicantRepository(
            Applicant(id = 1L, accountId = 10L, status = ApplicantStatus.SUBMITTED),
        )
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

        assertThrows(ApplicantAlreadyExistsException::class.java) {
            service.createApplicant(CreateApplicantCommand(10L))
        }
        assertNull(repository.savedApplicant)
    }

    @Test
    fun submitAndCancelPersistLifecycle() {
        val repository = FakeApplicantRepository(
            submittableGedApplicant(),
        )
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

        service.submit(accountId = 10L)
        assertEquals(ApplicantStatus.SUBMITTED, repository.savedApplicant?.status)
        assertNotNull(repository.savedApplicant?.submittedAt)
        assertEquals(170.0, repository.savedApplicant?.totalScore ?: 0.0, 0.0)
        assertNotNull(repository.savedApplicant?.totalScoreUpdatedAt)

        val canceled = service.cancel(10L, "개인 사유")
        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", repository.savedApplicant?.cancelReason)
    }

    @Test
    fun cancelRejectsDraftApplication() {
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)), OPEN, ACCEPT_PHONE)

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
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE, events::add)

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
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

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
        val service = ApplicationCommandService(repository, CLOSED, ACCEPT_PHONE, events::add)

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
    fun submittedApplicationRejectsModificationAndResubmission() {
        val repository = FakeApplicantRepository(
            Applicant(id = 1L, accountId = 10L, status = ApplicantStatus.SUBMITTED),
        )
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

        assertThrows(IllegalArgumentException::class.java) {
            service.updateIntroduction(accountId = 10L, introduction = "고친 소개")
        }
        assertThrows(IllegalArgumentException::class.java) { service.submit(accountId = 10L) }

        assertNull(repository.savedApplicant)
    }

    @Test
    fun cancelAndArrivalDoNotDependOnApplicationPeriod() {
        fun submitted() = FakeApplicantRepository(
            Applicant(id = 1L, accountId = 10L, status = ApplicantStatus.SUBMITTED, statusVersion = 2),
        )

        val canceled = ApplicationCommandService(submitted(), CLOSED, ACCEPT_PHONE).cancel(10L, null)
        val arrived = ApplicationCommandService(submitted(), CLOSED, ACCEPT_PHONE).updateArrival(UpdateApplicantArrivalCommand(1L, true))

        assertEquals(ApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals(ApplicantStatus.ARRIVAL, arrived.applicantStatus)
    }

    @Test
    fun deletePublishesDeletionAndAllowsSameAccountToCreateAgain() {
        val repository = FakeApplicantRepository(
            Applicant(id = 3L, accountId = 10L, status = ApplicantStatus.COMPLETED, statusVersion = 7),
        )
        val events = mutableListOf<ApplicantStatusChanged>()
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE, events::add)

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
            ACCEPT_PHONE,
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
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

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
        val service = ApplicationCommandService(FakeApplicantRepository(Applicant(id = 1L, accountId = 10L)), OPEN, ACCEPT_PHONE)
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
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

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
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

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

        val forms = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE).findApplicationForms(listOf(10L, 11L))

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
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)

        assertEquals(scores, service.findApplicationForm(10L)?.gedScores)

        // 졸업예정으로 바꿔도 학기 성적을 넣기 전까지 검정고시 점수가 남는다. 원서에는 싣지 않는다.
        service.updateType(10L, AdmissionType.REGULAR, Region.DAEJEON, GraduationType.PROSPECTIVE, YearMonth.of(2027, 2))
        assertNull(service.findApplicationForm(10L)?.gedScores)
    }

    @Test
    fun applicationFormRecalculatesAndSavesMissingSubmittedScore() {
        val applicant = submittableGedApplicant().apply {
            status = ApplicantStatus.SUBMITTED
            totalScore = null
            totalScoreUpdatedAt = null
        }

        val repository = FakeApplicantRepository(applicant)
        val form = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE).findApplicationForm(10L)

        assertEquals(170.0, form?.score?.totalScore ?: 0.0, 0.0)
        assertEquals(170.0, repository.savedApplicant?.totalScore ?: 0.0, 0.0)
        assertNotNull(repository.savedApplicant?.totalScoreUpdatedAt)
    }

    @Test
    fun scoreQueriesRecalculateMissingScoresOnlyOnce() {
        val queries: List<(ApplicationCommandService) -> Double?> = listOf(
            { it.findApplicant(1L)?.totalScore },
            { it.findApplicationForms(listOf(10L)).single().score?.totalScore },
            { it.listApplicants().first().totalScore },
        )
        queries.forEach { query ->
            val summary = ApplicantResult(
                applicantId = 1L, accountId = 10L, name = null, schoolName = null,
                region = null, admissionType = AdmissionType.REGULAR, photoFileId = null,
                birthdate = null, phoneNumber = null, graduationType = GraduationType.GED,
                totalScore = null, status = ApplicantStatus.SUBMITTED, submittedAt = null,
            )
            val repository = FakeApplicantRepository(submittableGedApplicant(), listOf(summary, summary.copy(applicantId = 2L, totalScore = 0.0)))
            val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)
            assertEquals(170.0, query(service) ?: 0.0, 0.0)
            assertEquals(170.0, query(service) ?: 0.0, 0.0)
            assertEquals(0.0, service.listApplicants().last().totalScore ?: -1.0, 0.0)
            assertEquals(1, repository.saveCount)
        }
    }

    @Test
    fun queriesKeepMissingScoreWhenGradesAreIncomplete() {
        val applicant = submittableGedApplicant()
        val incomplete = listOf(
            applicant.copy(admissionType = null),
            applicant.copy(graduationType = null),
            applicant.copy(academicRecord = null),
            applicant.copy(academicRecord = AcademicRecord()),
            applicant.copy(graduationType = GraduationType.PROSPECTIVE, academicRecord = AcademicRecord()),
            applicant.copy(graduationType = GraduationType.GRADUATED, academicRecord = AcademicRecord(
                subjectGrades = linkedMapOf(SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to all(SubjectGrade.X)),
            )),
        )
        incomplete.forEach {
            val repository = FakeApplicantRepository(it)
            val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)
            assertNull(service.findApplicant(1L)?.totalScore)
            assertNull(service.findApplicationForm(10L)?.score)
            assertNull(it.totalScoreUpdatedAt)
            assertEquals(0, repository.saveCount)
        }
    }

    @Test
    fun queriesPreserveSavedZeroScoreWithoutRecalculating() {
        val repository = FakeApplicantRepository(submittableGedApplicant().copy(totalScore = 0.0))
        val service = ApplicationCommandService(repository, OPEN, ACCEPT_PHONE)
        assertEquals(0.0, service.findApplicant(1L)?.totalScore ?: -1.0, 0.0)
        assertEquals(0.0, service.findApplicationForm(10L)?.score?.totalScore ?: -1.0, 0.0)
        assertEquals(0, repository.saveCount)
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
                submittableGedApplicant(),
            )

            ApplicationCommandService(repository, OPEN, ACCEPT_PHONE).submit(accountId = 10L)

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
        private val summaries: List<ApplicantResult> = emptyList(),
    ) : ApplicantRepository {
        var savedApplicant: Applicant? = null
        var saveCount = 0
        var deletedId: Long? = null

        override fun save(applicant: Applicant): Applicant {
            saveCount += 1
            savedApplicant = applicant
            this.applicant = applicant
            return applicant
        }

        // 목록은 쿼리가 거른다. ApplicantSummaryQueryTest 가 덮는다.
        override fun findSummariesByStatusIn(statuses: Set<ApplicantStatus>): List<ApplicantResult> =
            summaries

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
        val ACCEPT_PHONE = hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator { _, _ -> true }
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

        fun submittableGedApplicant() = Applicant(
            id = 1L,
            accountId = 10L,
            admissionType = AdmissionType.REGULAR,
            graduationType = GraduationType.GED,
            name = "홍길동",
            guardianName = "보호자",
            introduction = "소개",
            studyPlan = "학업 계획",
            academicRecord = AcademicRecord(gedScores = GedScores(100, 100, 100, 100, 100, 100, 100)),
        )
    }
}
