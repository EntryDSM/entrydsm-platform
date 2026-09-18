package hs.kr.entrydsm.application.application.port.`in`.result

data class MiddleSchoolSearchResult(
    val schools: List<MiddleSchoolResult>,
    val totalCount: Int,
)

data class MiddleSchoolResult(
    val code: String,
    val name: String,
    val address: String?,
)
