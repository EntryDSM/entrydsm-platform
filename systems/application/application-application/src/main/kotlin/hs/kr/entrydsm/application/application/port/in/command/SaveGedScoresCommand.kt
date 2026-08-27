package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.model.GedScores

data class SaveGedScoresCommand(
    val userId: Long? = null,
    val gedScores: GedScores,
)
