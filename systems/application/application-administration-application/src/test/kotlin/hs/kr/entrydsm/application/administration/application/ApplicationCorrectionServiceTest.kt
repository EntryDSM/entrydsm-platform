package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.ApplicationFormChanges
import hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.ApplicationCorrectionAuditRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationCorrectionState
import hs.kr.entrydsm.admin.domain.port.out.ApplicationCorrectionStatePort
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.model.Applicant
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ApplicationCorrectionServiceTest {
    @Test fun `제출과 접수 원서는 PATCH와 같은 구조로 실제 값과 빈 이유를 제공한다`() {
        for (status in listOf(ApplicantStatus.SUBMITTED, ApplicantStatus.ARRIVAL)) {
            val applicant = schoolApplicant().copy(status = status)
            val before = applicant.copy(academicRecord = applicant.academicRecord!!.copy(subjectGrades = LinkedHashMap(applicant.academicRecord!!.subjectGrades)))
            val result = service(applicant).editableFields(5)
            assertEquals(expectedSchoolFields(), result.fields)
            assertNull(result.reason)
            assertEquals(before, applicant)
        }
    }

    @Test fun `지원자 공개가 대기여도 내부 결과가 있으면 값은 보여주고 GET과 PATCH 수정 불가 이유를 유지한다`() {
        val cases = listOf(
            schoolApplicant().copy(status = ApplicantStatus.DRAFT) to false,
            schoolApplicant().copy(status = ApplicantStatus.CANCELED) to false,
            schoolApplicant().copy(status = ApplicantStatus.SUBMITTED, passResultType = ResultType.DOCUMENT) to false,
            schoolApplicant().copy(status = ApplicantStatus.ARRIVAL) to true)
        for ((applicant, hasResult) in cases) {
            val before = applicant.copy(academicRecord = applicant.academicRecord!!.copy(subjectGrades = LinkedHashMap(applicant.academicRecord!!.subjectGrades)))
            val service = service(applicant, hasResult)
            val fields = service.editableFields(5)
            assertEquals(expectedSchoolFields(), fields.fields)
            assertEquals("제출 또는 접수된 원서이며 전형 결과가 없어야 수정할 수 있습니다.", fields.reason)
            assertEquals(before, applicant)
            assertThrows(AdminDomainException::class.java) {
                service.correct(CorrectApplicationCommand(5, "수정 검증", ApplicationFormChanges(introduction = "변경")), "10")
            }
            assertEquals(before, applicant)
        }
    }

    @Test fun `검정고시는 선택 과목과 실제 영점을 보존하고 빈 학교는 null로 제공한다`() {
        val applicant = Applicant(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED,
            graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GED,
            academicRecord = hs.kr.entrydsm.application.domain.model.AcademicRecord(gedScores =
                hs.kr.entrydsm.application.domain.model.GedScores(100, 90, 80, 70, 60, 0)))
        val fields = service(applicant).editableFields(5).fields
        assertNull(fields.school)
        assertEquals("GED", fields.graduationType)
        assertTrue(fields.academicRecord!!.subjectGrades.isEmpty())
        assertEquals(hs.kr.entrydsm.admin.domain.command.GedScoresCorrection(100, 90, 80, 70, 60, 0), fields.academicRecord!!.gedScores)
    }

    @Test fun `작성하지 않은 값은 null이고 존재하지 않거나 잘못된 원서 ID는 거부한다`() {
        val fields = service(Applicant(id = 5, accountId = 10)).editableFields(5).fields
        assertNull(fields.name)
        assertNull(fields.school)
        assertNull(fields.academicRecord)
        assertNull(fields.graduationDate)
        assertEquals(hs.kr.entrydsm.admin.domain.enum.ErrorCode.APPLICANT_NOT_FOUND,
            assertThrows(AdminDomainException::class.java) { service(null).editableFields(5) }.errorCode)
        assertThrows(IllegalArgumentException::class.java) { service(null).editableFields(0) }
    }

    private fun schoolApplicant() = Applicant(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED,
        admissionType = hs.kr.entrydsm.application.domain.enum.AdmissionType.REGULAR,
        region = hs.kr.entrydsm.application.domain.enum.Region.NATIONAL,
        graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.PROSPECTIVE,
        graduationDate = java.time.YearMonth.of(2027, 2), photoFileId = "photo", name = "홍길동",
        phoneNumber = "010-1234-5678", gender = hs.kr.entrydsm.application.domain.enum.Gender.MALE,
        birthdate = java.time.LocalDate.of(2010, 3, 2),
        specialAdmissionType = hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType.NATIONAL_MERIT,
        guardianName = "보호자", guardianPhoneNumber = "010-9876-5432",
        guardianGender = hs.kr.entrydsm.application.domain.enum.Gender.FEMALE, guardianRelation = "모",
        zipCode = "34503", addressBase = "대전광역시", addressDetail = "101호", introduction = "자기소개", studyPlan = "학업계획",
        middleSchoolInfo = hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo("D100", "중학교", "30101", "042-123-4567", "담임", "노출하면 안 되는 학교 주소"),
        academicRecord = hs.kr.entrydsm.application.domain.model.AcademicRecord(1, 2, 3, 4, 15, true, false,
            linkedMapOf(hs.kr.entrydsm.application.domain.enum.SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to
                hs.kr.entrydsm.application.domain.model.SubjectGrades(
                    hs.kr.entrydsm.application.domain.enum.SubjectGrade.A, hs.kr.entrydsm.application.domain.enum.SubjectGrade.B,
                    hs.kr.entrydsm.application.domain.enum.SubjectGrade.C, hs.kr.entrydsm.application.domain.enum.SubjectGrade.D,
                    hs.kr.entrydsm.application.domain.enum.SubjectGrade.E, hs.kr.entrydsm.application.domain.enum.SubjectGrade.X,
                    hs.kr.entrydsm.application.domain.enum.SubjectGrade.A))),
        examineeNumber = "11001", totalScore = 170.0, statusVersion = 7)

    private fun expectedSchoolFields() = ApplicationFormChanges(
        admissionType = "GENERAL", region = "NATIONWIDE", graduationType = "PROSPECTIVE", graduationDate = "2027-02",
        photoFileId = "photo", name = "홍길동", phoneNumber = "010-1234-5678", gender = "MALE", birthdate = "2010-03-02",
        specialAdmissionType = "NATIONAL_MERIT", guardianName = "보호자", guardianPhoneNumber = "010-9876-5432",
        guardianGender = "FEMALE", guardianRelation = "모", zipCode = "34503", addressBase = "대전광역시", addressDetail = "101호",
        introduction = "자기소개", studyPlan = "학업계획",
        school = hs.kr.entrydsm.admin.domain.command.SchoolCorrection("D100", "중학교", "30101", "042-123-4567", "담임"),
        academicRecord = hs.kr.entrydsm.admin.domain.command.AcademicRecordCorrection(1, 2, 3, 4, 15, true, false,
            mapOf("THIRD_GRADE_FIRST_SEMESTER" to hs.kr.entrydsm.admin.domain.command.SubjectGradesCorrection("A", "B", "C", "D", "E", "X", "A"))))

    @Test fun `검정고시 정정에서 기본 다섯 과목 누락은 저장 전에 거부한다`() {
        val complete = hs.kr.entrydsm.admin.domain.command.GedScoresCorrection(100, 100, 100, 100, 100)
        val missing = listOf(complete.copy(korean = null), complete.copy(math = null), complete.copy(english = null),
            complete.copy(science = null), complete.copy(society = null))
        missing.forEach { scores ->
            val applicant = Applicant(id = 5, accountId = 10, status = ApplicantStatus.SUBMITTED,
                graduationType = hs.kr.entrydsm.application.domain.enum.GraduationType.GED)
            val command = CorrectApplicationCommand(5, "성적 정정", ApplicationFormChanges(academicRecord =
                hs.kr.entrydsm.admin.domain.command.AcademicRecordCorrection(0, 0, 0, 0, 0, false, false, gedScores = scores)))
            val failure = assertThrows(AdminDomainException::class.java) { service(applicant).correct(command, "admin") }
            assertEquals(hs.kr.entrydsm.admin.domain.enum.ErrorCode.INVALID_APPLICATION_CORRECTION, failure.errorCode)
        }
    }

    private fun service(applicant: Applicant?, hasResult: Boolean = false): ApplicationCorrectionService {
        val repository = proxy(ApplicantRepository::class.java) { method ->
            assertEquals("findForUpdate", method); applicant
        }
        return ApplicationCorrectionService(repository, ApplicationCorrectionStatePort { ApplicationCorrectionState(hasResult) },
            ApplicationCorrectionAuditRepository { error("조회 중 감사 기록을 저장하면 안 된다") },
            proxy(ApplicationPort::class.java) { error("조회 중 원서를 조회할 필요가 없다") },
            ApplicantStatusEventOutbox { error("조회 또는 거부된 수정은 이벤트를 발행하면 안 된다") },
            MiddleSchoolRepository { error("조회 중 학교를 조회할 필요가 없다") }, AccountPhoneValidator { _, _ -> true })
    }

    private fun <T> proxy(type: Class<T>, invoke: (String) -> Any?): T = type.cast(
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ -> invoke(method.name) })
}
