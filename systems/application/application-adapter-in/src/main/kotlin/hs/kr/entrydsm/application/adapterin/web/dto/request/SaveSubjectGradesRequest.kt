package hs.kr.entrydsm.application.adapterin.web.dto.request

data class SaveSubjectGradesRequest(
    val applicantId: Long,
    val schoolSemester: String,
    val subjects: SubjectGradesRequest,
)
