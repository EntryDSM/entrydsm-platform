package hs.kr.entrydsm.configuration.domain.document

/**
 * 요강 <서식 1> 입학원서를 찍는 데 쓰는 원서 내용. application 이 가진 값이라 작성 중이면 대부분 비어 있다.
 *
 * [Applicant] 와 달리 원서 전문이라 무겁다. 본인 판정에 쓰는 [userId] 는 둘 다 갖는다.
 */
data class ApplicationForm(
    /** 서식의 접수번호 칸에 찍는다. application 이 원서를 만든 순서대로 매긴 번호다. */
    val applicantId: Long,
    /** 원서 주인 계정. 본인 판정 기준이다. */
    val userId: Long,
    val name: String?,
    val phoneNumber: String?,
    /** yyyy-MM-dd */
    val birthdate: String?,
    val gender: Gender?,
    /** 우편번호까지 합친 한 줄 */
    val address: String?,
    /** 원서에 적힌 증명사진 공개 ID. 학생이 보낸 값이라 그대로 믿지 않는다. */
    val photoFileId: String?,
    val region: Applicant.Region?,
    val admissionType: Applicant.AdmissionType?,
    /** 서식의 특기사항 칸. 특별전형 구분이 없으면 null 이다. */
    val specialNote: String?,
    val graduationType: GraduationType?,
    /** yyyy-MM */
    val graduationDate: String?,
    val guardianName: String?,
    val guardianRelation: String?,
    val guardianPhoneNumber: String?,
    val school: MiddleSchool?,
    /**
     * 교과성적 표의 네 열. 순서는 서식과 같다 — 3학년 2학기, 3학년 1학기, 직전학기, 직전전학기.
     * 반영할 성적이 없는 열은 null 이다.
     */
    val semesterGrades: List<SemesterGrades?>,
    /** 출결·봉사·가산점. 원서에 성적을 한 번도 넣지 않았으면 null 이다. */
    val academicRecord: AcademicRecord?,
    /** 서식 3 에 찍는 자기소개서·학업계획서 본문. 지원자가 쓴 줄바꿈까지 그대로 옮긴다. */
    val introduction: String?,
    val studyPlan: String?,
) {
    /** @property label 원서에 찍는 한글 표기 */
    enum class Gender(val label: String) {
        MALE("남"),
        FEMALE("여"),
    }

    /** @property label 원서에 찍는 한글 표기 */
    enum class GraduationType(val label: String) {
        PROSPECTIVE("졸업예정"),
        GRADUATED("졸업"),
        GED("검정고시"),
    }

    data class MiddleSchool(
        val name: String,
        val studentNumber: String,
        val phone: String,
        val teacherName: String,
    )

    /** 한 학기 7과목의 성취도. 미이수(자유학기 등)는 빈 문자열이라 칸이 빈다. */
    data class SemesterGrades(
        val korean: String,
        val society: String,
        val history: String,
        val math: String,
        val science: String,
        val technology: String,
        val english: String,
    ) {
        /** 서식의 교과 행 순서 — 국어·사회·역사·수학·과학·기술·가정·영어 */
        fun inFormOrder(): List<String> = listOf(korean, society, history, math, science, technology, english)
    }

    data class AcademicRecord(
        val absentCount: Int,
        val lateCount: Int,
        val earlyLeaveCount: Int,
        val classAbsenceCount: Int,
        val volunteerTime: Int,
        val dsmAlgorithmAwarded: Boolean,
        val programmingCertified: Boolean,
    )

    companion object {
        /** 서식의 교과성적 표 열 수 */
        const val SEMESTER_COLUMN_COUNT = 4
    }
}
