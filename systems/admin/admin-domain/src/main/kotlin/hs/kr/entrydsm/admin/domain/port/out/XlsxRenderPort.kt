package hs.kr.entrydsm.admin.domain.port.out

/**
 * 표를 xlsx 바이트로 변환합니다.
 *
 * 어떤 열을 담을지는 호출하는 쪽이 정하고, 이 포트는 칸에 옮겨 적기만 합니다.
 */
interface XlsxRenderPort {
    /**
     * @param rows 행마다 [header] 순서로 담은 값. 숫자는 숫자 칸, null 은 빈 칸, 나머지는 글자 칸이 된다
     */
    fun render(sheetName: String, header: List<String>, rows: List<List<Any?>>): ByteArray
}
