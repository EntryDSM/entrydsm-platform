package hs.kr.entrydsm.admin.adapterout.document

import hs.kr.entrydsm.admin.domain.port.out.PdfMergePort
import java.io.ByteArrayOutputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.springframework.stereotype.Component

/**
 * PDF 여러 개를 받은 순서대로 이어 붙입니다.
 *
 * ponytail: 받은 PDF 와 결과를 모두 힙에 둔다. document 가 증명사진을 줄여 수험표 한 장이 수백 KB 라 1차 합격자
 * 규모는 감당한다. 커지면 임시 파일 캐시(`IOUtils.createTempFileOnlyStreamCache`)로 합쳐 스트림으로 올린다.
 */
@Component
class PdfBoxMergeAdapter : PdfMergePort {

    override fun merge(pdfs: List<ByteArray>): ByteArray {
        require(pdfs.isNotEmpty()) { "No PDF to merge" }
        // PDFMergerUtility.mergeDocuments 처럼 원본은 합친 문서를 저장한 뒤에 닫는다.
        val sources = mutableListOf<PDDocument>()
        try {
            return PDDocument().use { merged ->
                val merger = PDFMergerUtility()
                pdfs.forEach { pdf -> merger.appendDocument(merged, Loader.loadPDF(pdf).also(sources::add)) }
                ByteArrayOutputStream().also { merged.save(it) }.toByteArray()
            }
        } finally {
            sources.forEach(PDDocument::close)
        }
    }
}
