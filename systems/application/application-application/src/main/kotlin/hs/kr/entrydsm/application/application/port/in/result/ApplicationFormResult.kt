package hs.kr.entrydsm.application.application.port.`in`.result

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.service.ScoreBreakdown
import java.time.LocalDate
import java.time.YearMonth

/**
 * 요강 <서식 1> 입학원서를 찍는 데 쓰는 원서 내용.
 *
 * [ApplicantResult] 와 따로 두는 것은 목록 조회가 제출 원서 전체를 한 번에 나르기 때문이다.
 * 제출 검증이 요구하는 값이 적어 작성 중이 아닌 원서에도 중학교·성적 칸은 비어 있을 수 있다.
 */
data class ApplicationFormResult(
    val applicantId: Long,
    val accountId: Long,
    val status: ApplicantStatus,
    val name: String?,
    val phoneNumber: String?,
    val birthdate: LocalDate?,
    val gender: Gender?,
    /** 우편번호까지 합친 한 줄 */
    val address: String?,
    val photoFileId: String?,
    val region: Region?,
    val admissionType: AdmissionType?,
    val specialAdmissionType: SpecialAdmissionType,
    val graduationType: GraduationType?,
    val graduationDate: YearMonth?,
    val guardianName: String?,
    val guardianRelation: String?,
    val guardianPhoneNumber: String?,
    val middleSchool: MiddleSchoolInfo?,
    /**
     * 서식 1 교과성적 표의 네 열. 반영할 성적이 없는 열은 null 이다.
     * 직전·직전전은 절대 학기가 아니라 자유학기를 건너뛴 상대 순서다.
     */
    val thirdGradeSecondSemester: SubjectGrades?,
    val thirdGradeFirstSemester: SubjectGrades?,
    val previousSemester: SubjectGrades?,
    val secondPreviousSemester: SubjectGrades?,
    /** 출결·봉사·가산점. 성적을 한 번도 넣지 않았으면 null 이라 전부 0 인 것과 구분된다. */
    val academicRecord: AcademicRecord?,
    val score: ScoreBreakdown?,
    /** 서식 3 자기소개서·학업계획서 본문. 제출 검증이 요구하므로 제출본에는 차 있다. */
    val introduction: String?,
    val studyPlan: String?,
)
