package hs.kr.entrydsm.admin.domain.port.out

/**
 * 여러 PDF 를 받은 순서대로 이어 붙여 PDF 하나로 만듭니다.
 */
interface PdfMergePort {
    fun merge(pdfs: List<ByteArray>): ByteArray
}
