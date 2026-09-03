package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.io.Serializable

@Embeddable
data class SubjectGradeId(
    @Column(name = "academic_record_id")
    var academicRecordId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "school_semester", length = 20)
    var schoolSemester: SchoolSemester? = null,
) : Serializable
