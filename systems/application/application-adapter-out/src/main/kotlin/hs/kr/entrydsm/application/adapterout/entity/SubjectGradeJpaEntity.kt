package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table

@Entity
@Table(name = "subject_grades")
open class SubjectGradeJpaEntity(
    @EmbeddedId
    var id: SubjectGradeId = SubjectGradeId(),

    @MapsId("academicRecordId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_record_id")
    open var academicRecord: AcademicRecordJpaEntity? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "korean_grade", nullable = false, length = 2)
    var koreanGrade: SubjectGrade = SubjectGrade.X,

    @Enumerated(EnumType.STRING)
    @Column(name = "math_grade", nullable = false, length = 2)
    var mathGrade: SubjectGrade = SubjectGrade.X,

    @Enumerated(EnumType.STRING)
    @Column(name = "english_grade", nullable = false, length = 2)
    var englishGrade: SubjectGrade = SubjectGrade.X,

    @Enumerated(EnumType.STRING)
    @Column(name = "science_grade", nullable = false, length = 2)
    var scienceGrade: SubjectGrade = SubjectGrade.X,

    @Enumerated(EnumType.STRING)
    @Column(name = "society_grade", nullable = false, length = 2)
    var societyGrade: SubjectGrade = SubjectGrade.X,

    @Enumerated(EnumType.STRING)
    @Column(name = "technology_grade", nullable = false, length = 2)
    var technologyGrade: SubjectGrade = SubjectGrade.X,

    @Enumerated(EnumType.STRING)
    @Column(name = "history_grade", nullable = false, length = 2)
    var historyGrade: SubjectGrade = SubjectGrade.X,
) {
    fun updateFrom(domain: SubjectGrades) {
        koreanGrade = domain.koreanGrade
        mathGrade = domain.mathGrade
        englishGrade = domain.englishGrade
        scienceGrade = domain.scienceGrade
        societyGrade = domain.societyGrade
        technologyGrade = domain.technologyGrade
        historyGrade = domain.historyGrade
    }

    fun toDomain(): SubjectGrades =
        SubjectGrades(
            koreanGrade = koreanGrade,
            mathGrade = mathGrade,
            englishGrade = englishGrade,
            scienceGrade = scienceGrade,
            societyGrade = societyGrade,
            technologyGrade = technologyGrade,
            historyGrade = historyGrade,
        )
}
