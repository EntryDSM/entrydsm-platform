package hs.kr.entrydsm.application.adapterin.web.dto.response

data class MiddleSchoolSearchResponse(
    val schools: List<MiddleSchoolResponse>,
    val hasNext: Boolean,
)

data class MiddleSchoolResponse(
    val code: String,
    val name: String,
)
