package hs.kr.entrydsm.application.adapterin.web.dto.request

import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.SubjectGrades

data class SubjectGradesRequest(
    val koreanGrade: SubjectGrade,
    val societyGrade: SubjectGrade,
    val englishGrade: SubjectGrade,
    val historyGrade: SubjectGrade,
    val mathGrade: SubjectGrade,
    val scienceGrade: SubjectGrade,
    val technologyGrade: SubjectGrade,
) {
    fun toDomain(): SubjectGrades =
        SubjectGrades(
            koreanGrade = koreanGrade,
            societyGrade = societyGrade,
            englishGrade = englishGrade,
            historyGrade = historyGrade,
            mathGrade = mathGrade,
            scienceGrade = scienceGrade,
            technologyGrade = technologyGrade,
        )
}
