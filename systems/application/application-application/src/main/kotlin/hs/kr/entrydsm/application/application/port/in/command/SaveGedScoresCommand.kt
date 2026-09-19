package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.model.GedScores

data class SaveGedScoresCommand(
    val accountId: Long? = null,
    val gedScores: GedScores,
)
