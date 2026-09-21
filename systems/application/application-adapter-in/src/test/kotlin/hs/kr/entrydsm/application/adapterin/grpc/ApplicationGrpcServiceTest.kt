package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.SpecialAdmissionType as GrpcSpecialAdmissionType
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ApplicationGrpcServiceTest {
    private lateinit var port: FakeApplicationPort
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: ApplicationServiceGrpc.ApplicationServiceBlockingStub

    @Before
    fun setUp() {
        port = FakeApplicationPort()
        server = ServerBuilder.forPort(0).addService(ApplicationGrpcService(port)).build().start()
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.port).usePlaintext().build()
        stub = ApplicationServiceGrpc.newBlockingStub(channel)
    }

    @After
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun servesCreateGetAndCancelContract() {
        val created = stub.createApplication(CreateApplicationRequest.newBuilder().setUserId(USER_ID).build())
        val found = stub.getApplication(GetApplicationRequest.newBuilder().setUserId(USER_ID).build())
        val canceled = stub.cancelApplication(
            CancelApplicationRequest.newBuilder().setUserId(USER_ID).setReason("개인 사유").build(),
        )

        assertEquals(hs.kr.entrydsm.application.grpc.ApplicantStatus.APPLICANT_STATUS_DRAFT, created.applicantStatus)
        assertEquals(hs.kr.entrydsm.application.grpc.PassStatus.PASS_STATUS_NOT_ANNOUNCED, found.passStatus)
        assertFalse(found.hasSubmittedAtEpochMillis())
        assertEquals(hs.kr.entrydsm.application.grpc.ApplicantStatus.APPLICANT_STATUS_CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", port.cancelReason)
        assertEquals(2, port.findCount)
    }

    @Test
    fun mapsInvalidAndMissingUsers() {
        val invalid = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplication(GetApplicationRequest.newBuilder().setUserId(0).build())
        }
        val missing = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplication(GetApplicationRequest.newBuilder().setUserId(404).build())
        }

        assertEquals(Status.Code.INVALID_ARGUMENT, invalid.status.code)
        assertEquals(Status.Code.NOT_FOUND, missing.status.code)
    }

    @Test
    fun servesApplicantWithUnsetFieldsLeftEmpty() {
        port.applicant = ApplicantResult(
            applicantId = APPLICANT_ID,
            accountId = USER_ID,
            name = "홍길동",
            schoolName = null,
            region = Region.DAEJEON,
            admissionType = null,
            photoFileId = "photo_3f2c9a1e0b7d4c55a1e2f3b4c5d6e7f8",
            birthdate = null,
            phoneNumber = null,
            graduationType = null,
            totalScore = null,
            status = ApplicantStatus.SUBMITTED,
            submittedAt = null,
            gender = Gender.FEMALE,
            address = "충청남도 천안시",
        )

        val found = stub.getApplicant(GetApplicantRequest.newBuilder().setApplicantId(APPLICANT_ID).build())
        val missing = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplicant(GetApplicantRequest.newBuilder().setApplicantId(404).build())
        }

        assertEquals(APPLICANT_ID, found.applicantId)
        assertEquals(USER_ID, found.userId)
        assertEquals("홍길동", found.name)
        assertFalse(found.hasSchoolName())
        assertEquals(GrpcRegion.REGION_DAEJEON, found.region)
        assertEquals(GrpcAdmissionType.ADMISSION_TYPE_UNSPECIFIED, found.admissionType)
        assertEquals("photo_3f2c9a1e0b7d4c55a1e2f3b4c5d6e7f8", found.photoFileId)
        assertEquals(GrpcGender.GENDER_FEMALE, found.gender)
        assertEquals("충청남도 천안시", found.address)
        assertEquals(Status.Code.NOT_FOUND, missing.status.code)
    }

    @Test
    fun servesApplicationFormByAccountWithUnsetFieldsLeftEmpty() {
        port.form = ApplicationFormResult(
            applicantId = APPLICANT_ID,
            accountId = USER_ID,
            status = ApplicantStatus.SUBMITTED,
            name = "홍길동",
            phoneNumber = null,
            birthdate = LocalDate.of(2010, 3, 2),
            gender = Gender.MALE,
            address = "(34503) 대전광역시 유성구 가정북로 76 101동 1001호",
            photoFileId = "photo_3f2c9a1e0b7d4c55a1e2f3b4c5d6e7f8",
            region = Region.DAEJEON,
            admissionType = null,
            specialAdmissionType = SpecialAdmissionType.NATIONAL_MERIT,
            graduationType = GraduationType.PROSPECTIVE,
            graduationDate = YearMonth.of(2027, 2),
            guardianName = "홍판서",
            guardianRelation = "부",
            guardianPhoneNumber = null,
            middleSchool = MiddleSchoolInfo(
                "D100000",
                "대덕중학교",
                "30115",
                "042-000-0000",
                "김선생",
                schoolAddress = "대전광역시 대덕구 중리로 1",
            ),
            thirdGradeSecondSemester = null,
            thirdGradeFirstSemester = SubjectGrades(
                koreanGrade = SubjectGrade.A,
                societyGrade = SubjectGrade.B,
                historyGrade = SubjectGrade.C,
                mathGrade = SubjectGrade.X,
                scienceGrade = SubjectGrade.D,
                technologyGrade = SubjectGrade.E,
                englishGrade = SubjectGrade.A,
            ),
            previousSemester = null,
            secondPreviousSemester = null,
            academicRecord = AcademicRecord(volunteerTime = 30, isDsmAlgorithmAwarded = true),
            introduction = "저는 …",
            studyPlan = "입학 후 …",
        )

        val found = stub.getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(USER_ID).build())
        val missing = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(404).build())
        }
        val invalid = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(0).build())
        }

        // 계정으로 찾지만 서식의 접수번호 칸은 원서 ID 다.
        assertEquals(APPLICANT_ID, found.applicantId)
        assertEquals(USER_ID, found.userId)
        assertEquals("2010-03-02", found.birthdate)
        assertEquals("2027-02", found.graduationDate)
        assertEquals(GrpcGender.GENDER_MALE, found.gender)
        assertEquals(GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE, found.graduationType)
        assertEquals(
            GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_NATIONAL_MERIT,
            found.specialAdmissionType,
        )
        assertEquals("D100000", found.middleSchool.code)
        assertEquals("대덕중학교", found.middleSchool.name)
        assertEquals("김선생", found.middleSchool.teacherName)
        assertEquals("대전광역시 대덕구 중리로 1", found.middleSchool.address)
        // 성취도 미이수(X)는 요강에 없는 값이라 빈 문자열로 나가 칸이 빈다.
        assertEquals("A", found.thirdGradeFirstSemester.korean)
        assertEquals("", found.thirdGradeFirstSemester.math)
        assertEquals(30, found.academicRecord.volunteerTime)
        assertTrue(found.academicRecord.dsmAlgorithmAwarded)
        assertFalse(found.academicRecord.programmingCertified)
        // 비어 있는 값은 담지 않아 서식의 칸이 빈다.
        assertFalse(found.hasPhoneNumber())
        assertFalse(found.hasGuardianPhoneNumber())
        assertFalse(found.hasThirdGradeSecondSemester())
        assertEquals(GrpcAdmissionType.ADMISSION_TYPE_UNSPECIFIED, found.admissionType)

        assertEquals(Status.Code.NOT_FOUND, missing.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, invalid.status.code)

        // 기관코드 표에 주소가 없는 학교는 주소를 담지 않아 출신지역 칸이 빈다.
        port.form = port.form?.copy(
            middleSchool = MiddleSchoolInfo("D100000", "대덕중학교", "30115", "042-000-0000", "김선생"),
        )
        val withoutAddress = stub.getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(USER_ID).build())
        assertEquals("D100000", withoutAddress.middleSchool.code)
        assertFalse(withoutAddress.middleSchool.hasAddress())
    }

    private class FakeApplicationPort : ApplicationPort {
        private var snapshot: ApplicationSnapshotResult? = null
        var applicant: ApplicantResult? = null
        var applicants: List<ApplicantResult> = emptyList()
        var form: ApplicationFormResult? = null
        var cancelReason: String? = null
        var findCount = 0

        override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
            snapshot = snapshot(command.accountId ?: error("accountId is required"), ApplicantStatus.DRAFT)
            return CreateApplicantResult(1L, requireNotNull(snapshot))
        }

        override fun listApplicants(): List<ApplicantResult> = applicants

        override fun findByAccountId(accountId: Long): ApplicationSnapshotResult? {
            findCount += 1
            return snapshot?.takeIf { it.accountId == accountId }
        }

        override fun findApplicant(applicantId: Long): ApplicantResult? =
            applicant?.takeIf { it.applicantId == applicantId }

        override fun findApplicationForm(accountId: Long): ApplicationFormResult? =
            form?.takeIf { it.accountId == accountId }

        override fun cancel(accountId: Long, reason: String?): ApplicationSnapshotResult {
            cancelReason = reason
            return snapshot(accountId, ApplicantStatus.CANCELED).also { snapshot = it }
        }

        override fun updateType(command: UpdateTypeCommand) = Unit
        override fun updatePersonal(command: UpdatePersonalCommand) = Unit
        override fun updateFamily(command: UpdateFamilyCommand) = Unit
        override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) = Unit
        override fun updateIntroduction(command: UpdateIntroductionCommand) = Unit
        override fun updateStudyPlan(command: UpdateStudyPlanCommand) = Unit
        override fun submit(command: SubmitApplicationCommand) = Unit
        override fun getLanding(accountId: Long?): LandingResult = LandingResult(null)

        private fun snapshot(accountId: Long, status: ApplicantStatus) = ApplicationSnapshotResult(
            accountId = accountId,
            applicantStatus = status,
            submittedAt = null,
            updatedAt = LocalDateTime.of(2026, 9, 9, 0, 0),
            passStatus = PassResultStatus.PENDING,
            announcedAt = null,
        )
    }

    private companion object {
        const val USER_ID = 10L
        const val APPLICANT_ID = 3L
    }
}
