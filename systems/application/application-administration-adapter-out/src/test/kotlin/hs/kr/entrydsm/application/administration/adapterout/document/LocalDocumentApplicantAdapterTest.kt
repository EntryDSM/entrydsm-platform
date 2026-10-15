package hs.kr.entrydsm.application.administration.adapterout.document

import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.grpcmapping.toGrpcForm
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDocumentApplicantAdapterTest {
    @Test
    fun `검정고시 기술가정 protobuf 존재 여부를 문서 빈칸과 실제 영점으로 변환한다`() {
        listOf(null, 0).forEach { technology ->
            val form = form(technology)
            val protobuf = form.toGrpcForm()
            assertTrue(protobuf.hasGedScores())
            assertEquals(technology != null, protobuf.gedScores.hasTechnology())
            if (technology != null) assertEquals(0, protobuf.gedScores.technology)
            val applications = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicationPort::class.java)) { _, method, args ->
                assertEquals("findApplicationForm", method.name)
                assertEquals(10L, args[0])
                form
            } as ApplicationPort

            val actual = requireNotNull(LocalDocumentApplicantAdapter(applications).findApplicationForm(10L))
            assertEquals(5L, actual.applicantId)
            val ged = requireNotNull(actual.gedScores)
            assertEquals(if (technology == null) "" else "0", ged.technology)
            assertEquals("", ged.history)
            assertEquals("100", ged.korean)
            assertEquals("100", ged.english)
        }
    }

    private fun form(technology: Int?) = ApplicationFormResult(
        applicantId = 5, accountId = 10, status = ApplicantStatus.DRAFT, name = null,
        phoneNumber = null, birthdate = null, gender = null, address = null, photoFileId = null,
        region = null, admissionType = null, specialAdmissionType = SpecialAdmissionType.NONE,
        graduationType = GraduationType.GED, graduationDate = null, guardianName = null,
        guardianRelation = null, guardianPhoneNumber = null, middleSchool = null,
        thirdGradeSecondSemester = null, thirdGradeFirstSemester = null, previousSemester = null,
        secondPreviousSemester = null, academicRecord = null, score = null,
        introduction = null, studyPlan = null, gedScores = GedScores(100, 100, 100, 100, 100, technology),
    )
}
