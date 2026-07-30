package hs.kr.entrydsm.application.adapterin.web.dto.request

data class SaveGedScoresRequest(
    val applicantId: Long,
    val koreanScore: Int,
    val societyScore: Int,
    val englishScore: Int,
    val historyScore: Int,
    val mathScore: Int,
    val scienceScore: Int,
    val technologyScore: Int,
)
