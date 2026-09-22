package hs.kr.entrydsm.admin.adapterout.document

import java.io.ByteArrayOutputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfBoxMergeAdapterTest {

    @Test
    fun `받은 순서대로 이어 붙여 PDF 하나로 만든다`() {
        // 쪽 크기로 어느 PDF 에서 온 쪽인지 가린다.
        val merged = PdfBoxMergeAdapter().merge(listOf(pdf(PDRectangle.A4, pages = 1), pdf(PDRectangle.A5, pages = 2)))

        Loader.loadPDF(merged).use { document ->
            assertEquals(
                listOf(PDRectangle.A4.width, PDRectangle.A5.width, PDRectangle.A5.width),
                document.pages.map { it.mediaBox.width },
            )
        }
    }

    @Test
    fun `이어 붙일 PDF가 없으면 빈 파일을 만들지 않고 실패한다`() {
        assertThrows(IllegalArgumentException::class.java) { PdfBoxMergeAdapter().merge(emptyList()) }
    }

    private fun pdf(size: PDRectangle, pages: Int): ByteArray =
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage(size)) }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
}
