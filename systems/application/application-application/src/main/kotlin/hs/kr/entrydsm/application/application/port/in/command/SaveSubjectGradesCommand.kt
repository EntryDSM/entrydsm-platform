package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.model.SubjectGrades

data class SaveSubjectGradesCommand(
    val userId: Long? = null,
    val schoolSemester: SchoolSemester,
    val subjectGrades: SubjectGrades,
)
