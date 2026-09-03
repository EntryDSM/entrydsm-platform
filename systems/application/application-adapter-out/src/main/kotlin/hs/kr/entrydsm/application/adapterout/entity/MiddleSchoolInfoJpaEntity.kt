package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "middle_school_infos")
open class MiddleSchoolInfoJpaEntity(
    @Id
    @Column(name = "applicant_id")
    var applicantId: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    var applicant: ApplicantJpaEntity? = null,

    @Column(name = "school_name", nullable = false, length = 50)
    var schoolName: String = "",

    @Column(name = "student_number", nullable = false, length = 8)
    var studentNumber: String = "",

    @Column(name = "school_phone", nullable = false, length = 16)
    var schoolPhone: String = "",

    @Column(name = "teacher_name", nullable = false, length = 20)
    var teacherName: String = "",
) {
    fun updateFrom(domain: MiddleSchoolInfo) {
        schoolName = domain.schoolName
        studentNumber = domain.studentNumber
        schoolPhone = domain.schoolPhone
        teacherName = domain.teacherName
    }

    fun toDomain(): MiddleSchoolInfo =
        MiddleSchoolInfo(
            schoolName = schoolName,
            studentNumber = studentNumber,
            schoolPhone = schoolPhone,
            teacherName = teacherName,
        )
}
