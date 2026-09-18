package hs.kr.entrydsm.application.application.port.`in`.command

data class SearchMiddleSchoolCommand(
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }
}
