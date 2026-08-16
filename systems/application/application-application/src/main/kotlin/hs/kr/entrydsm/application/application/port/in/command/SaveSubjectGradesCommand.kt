package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.model.SubjectGrades

data class SaveSubjectGradesCommand(
    val applicantId: Long,
    val authorization: String? = null,
    val userId: Long? = null,
    val schoolSemester: SchoolSemester,
    val subjectGrades: SubjectGrades,
)
