package hs.kr.entrydsm.admin.domain.port.out

/**
 * XHTML 문서를 PDF 바이트로 변환합니다.
 *
 * 레이아웃은 도메인의 `AdmissionTicketHtml`이 만들고, 이 포트는 변환만 책임집니다.
 */
interface PdfRenderPort {
    fun render(html: String): ByteArray
}
