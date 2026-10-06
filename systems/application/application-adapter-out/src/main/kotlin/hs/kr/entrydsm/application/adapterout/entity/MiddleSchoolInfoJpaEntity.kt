package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "school_code",
        referencedColumnName = "code",
        insertable = false,
        updatable = false,
    )
    var institutionCode: InstitutionCodeJpaEntity? = null,

    @Column(name = "school_code", nullable = false, length = 20)
    var schoolCode: String = "",

    @Column(name = "school_name", nullable = false, length = 50)
    var schoolName: String = "",

    @Column(name = "student_number", nullable = false, length = 8)
    var studentNumber: String = "",

    @Column(name = "school_phone", nullable = false, length = 16)
    var schoolPhone: String = "",

    @Column(name = "teacher_name", nullable = false, length = 20)
    var teacherName: String = "",
) {
    /** schoolAddress 는 기관코드 표의 값이라 쓰지 않는다. */
    fun updateFrom(domain: MiddleSchoolInfo) {
        schoolCode = domain.schoolCode
        schoolName = domain.schoolName
        studentNumber = domain.studentNumber
        schoolPhone = domain.schoolPhone
        teacherName = domain.teacherName
    }

    /**
     * 주소는 institutionCode 를 따라 읽는다. 저장 어댑터가 학교 변경 시 연관도 갱신한다.
     */
    fun toDomain(): MiddleSchoolInfo =
        MiddleSchoolInfo(
            schoolCode = schoolCode,
            schoolName = schoolName,
            studentNumber = studentNumber,
            schoolPhone = schoolPhone,
            teacherName = teacherName,
            schoolAddress = institutionCode?.address,
        )
}
