package hs.kr.entrydsm.configuration.adapterout

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import hs.kr.entrydsm.configuration.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.configuration.domain.document.port.out.PdfRenderPort
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

internal const val FONT_RESOURCE = "/fonts/NanumGothic-Regular.ttf"

/**
 * XHTML 을 PDF 로 바꾼다.
 *
 * openhtmltopdf 는 시스템 폰트를 쓰지 않아 한글 폰트를 직접 등록해야 한다. 폰트를 File 로 받으므로
 * 클래스패스 리소스를 처음 쓸 때 임시 파일로 한 번 풀어 둔다.
 */
@Component
class OpenHtmlToPdfAdapter : PdfRenderPort {

    private val fontFile: File by lazy { extractFont() }

    override fun render(html: String): ByteArray =
        ByteArrayOutputStream().use { output ->
            PdfRendererBuilder()
                .useFont(fontFile, AdmissionTicketHtml.FONT_FAMILY)
                .withHtmlContent(html, null)
                .toStream(output)
                .run()
            output.toByteArray()
        }

    private fun extractFont(): File {
        val resource = checkNotNull(javaClass.getResourceAsStream(FONT_RESOURCE)) { "Font not found: $FONT_RESOURCE" }
        return resource.use { stream ->
            Files.createTempFile("configuration-admission-ticket-", ".ttf").toFile().also { file ->
                file.deleteOnExit()
                file.outputStream().use(stream::copyTo)
            }
        }
    }
}
