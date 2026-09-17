package hs.kr.entrydsm.configuration.domain.document.port.out

/** XHTML 을 PDF 로 바꾼다. 레이아웃은 AdmissionTicketHtml 이 만든다. */
interface PdfRenderPort {
    fun render(html: String): ByteArray
}
