package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.model.AcademicRecord
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "academic_records")
open class AcademicRecordJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false, unique = true)
    var applicant: ApplicantJpaEntity? = null,

    @Column(name = "absent_count", nullable = false)
    var absentCount: Int = 0,

    @Column(name = "late_count", nullable = false)
    var lateCount: Int = 0,

    @Column(name = "early_leave_count", nullable = false)
    var earlyLeaveCount: Int = 0,

    @Column(name = "class_absence_count", nullable = false)
    var classAbsenceCount: Int = 0,

    @Column(name = "volunteer_time", nullable = false)
    var volunteerTime: Int = 0,

    @Column(name = "is_dsm_algorithm_awarded", nullable = false)
    var isDsmAlgorithmAwarded: Boolean = false,

    @Column(name = "is_programming_certified", nullable = false)
    var isProgrammingCertified: Boolean = false,

    @OneToMany(mappedBy = "academicRecord", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var subjectGrades: MutableList<SubjectGradeJpaEntity> = mutableListOf(),

    @OneToOne(mappedBy = "academicRecord", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var gedScores: GedScoreJpaEntity? = null,
) {
    fun updateFrom(domain: AcademicRecord) {
        absentCount = domain.absentCount
        lateCount = domain.lateCount
        earlyLeaveCount = domain.earlyLeaveCount
        classAbsenceCount = domain.classAbsenceCount
        volunteerTime = domain.volunteerTime
        isDsmAlgorithmAwarded = domain.isDsmAlgorithmAwarded
        isProgrammingCertified = domain.isProgrammingCertified
        subjectGrades.clear()
        subjectGrades.addAll(
            domain.subjectGrades.map { (semester, grades) ->
                SubjectGradeJpaEntity(
                    id = SubjectGradeId(schoolSemester = semester),
                    academicRecord = this,
                ).apply { updateFrom(grades) }
            },
        )
        gedScores = domain.gedScores?.let {
            GedScoreJpaEntity(academicRecord = this).apply { updateFrom(it) }
        }
    }

    fun toDomain(): AcademicRecord =
        AcademicRecord(
            absentCount = absentCount,
            lateCount = lateCount,
            earlyLeaveCount = earlyLeaveCount,
            classAbsenceCount = classAbsenceCount,
            volunteerTime = volunteerTime,
            isDsmAlgorithmAwarded = isDsmAlgorithmAwarded,
            isProgrammingCertified = isProgrammingCertified,
            subjectGrades = subjectGrades
                .associate { requireNotNull(it.id.schoolSemester) to it.toDomain() }
                .toMutableMap(),
            gedScores = gedScores?.toDomain(),
        )
}
