package hs.kr.entrydsm.application.application.port.`in`.command

data class SearchMiddleSchoolCommand(
    val name: String,
    val page: Int,
    val size: Int,
) {
    init {
        require(page >= 0) { "page must be greater than or equal to 0" }
        require(size in 1..MAX_SIZE) { "size must be between 1 and $MAX_SIZE" }
        require(page.toLong() * size <= Int.MAX_VALUE) { "page is too large" }
    }

    private companion object {
        const val MAX_SIZE = 100
    }
}
