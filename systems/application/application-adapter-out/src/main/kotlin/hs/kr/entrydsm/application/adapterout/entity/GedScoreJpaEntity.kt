package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.model.GedScores
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "ged_scores")
open class GedScoreJpaEntity(
    @Id
    @Column(name = "academic_record_id")
    var academicRecordId: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_record_id")
    var academicRecord: AcademicRecordJpaEntity? = null,

    @Column(name = "korean_score", nullable = false)
    var koreanScore: Int = 0,

    @Column(name = "math_score", nullable = false)
    var mathScore: Int = 0,

    @Column(name = "english_score", nullable = false)
    var englishScore: Int = 0,

    @Column(name = "science_score", nullable = false)
    var scienceScore: Int = 0,

    @Column(name = "society_score", nullable = false)
    var societyScore: Int = 0,

    @Column(name = "technology_score", nullable = false)
    var technologyScore: Int = 0,

    @Column(name = "history_score", nullable = false)
    var historyScore: Int = 0,
) {
    fun updateFrom(domain: GedScores) {
        koreanScore = domain.koreanScore
        mathScore = domain.mathScore
        englishScore = domain.englishScore
        scienceScore = domain.scienceScore
        societyScore = domain.societyScore
        technologyScore = domain.technologyScore
        historyScore = domain.historyScore
    }

    fun toDomain(): GedScores =
        GedScores(
            koreanScore = koreanScore,
            mathScore = mathScore,
            englishScore = englishScore,
            scienceScore = scienceScore,
            societyScore = societyScore,
            technologyScore = technologyScore,
            historyScore = historyScore,
        )
}
