package hs.kr.entrydsm.application.adapterin.web.dto.request

import tools.jackson.databind.annotation.JsonDeserialize

data class SaveGedScoresRequest(
    val koreanScore: Int,
    val societyScore: Int,
    val englishScore: Int,
    val mathScore: Int,
    val scienceScore: Int,
    @param:JsonDeserialize(using = GedTechnologyScoreDeserializer::class)
    val technologyScore: Int? = null,
)
