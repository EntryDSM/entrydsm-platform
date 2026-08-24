package hs.kr.entrydsm.application.adapterin.web.dto.request

data class SaveSubjectGradesRequest(
    val schoolSemester: String,
    val subjects: SubjectGradesRequest,
)
