package hs.kr.entrydsm.admin.domain.model

private const val DEFAULT_PAGE = 1
private const val DEFAULT_SIZE = 10
private const val MAX_SIZE = 100

/**
 * 목록 조회 요청의 페이지 조건입니다. 공통 규약대로 `page`는 1부터 시작합니다.
 *
 * 범위를 벗어난 값은 예외 대신 허용 범위로 맞춥니다. 관리자 화면의 페이지 이동은
 * 실패시키는 것보다 가장 가까운 유효 페이지를 보여주는 편이 쓸모 있기 때문입니다.
 */
data class PageRequest(
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    val normalizedPage: Int = page.coerceAtLeast(DEFAULT_PAGE)
    val normalizedSize: Int = size.coerceIn(1, MAX_SIZE)
}

/**
 * 목록 조회 결과입니다.
 */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int =
        if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()
}
