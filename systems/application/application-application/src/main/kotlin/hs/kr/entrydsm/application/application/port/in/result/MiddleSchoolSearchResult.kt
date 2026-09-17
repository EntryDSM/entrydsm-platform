package hs.kr.entrydsm.application.application.port.`in`.result

data class MiddleSchoolSearchResult(
    val schools: List<MiddleSchoolResult>,
    val hasNext: Boolean,
)

data class MiddleSchoolResult(
    val code: String,
    val name: String,
)
