package hs.kr.entrydsm.admin.adapterout.document

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.PdfRenderPort
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import org.springframework.stereotype.Component

private const val FONT_RESOURCE = "/fonts/NanumGothic-Regular.ttf"
private const val FONT_FAMILY = "AdmissionTicket"

/**
 * XHTML을 PDF로 변환합니다.
 *
 * openhtmltopdf는 시스템 폰트를 쓰지 않으므로 한글 폰트를 직접 등록해야 합니다.
 * 폰트는 `File`로만 받기 때문에, 클래스패스 리소스를 기동 시 임시 파일로 한 번 풀어 둡니다.
 */
@Component
class OpenHtmlToPdfAdapter : PdfRenderPort {

    private val fontFile: File by lazy { extractFont() }

    override fun render(html: String): ByteArray =
        runCatching {
            ByteArrayOutputStream().use { output ->
                PdfRendererBuilder()
                    .useFastMode()
                    .useFont(fontFile, FONT_FAMILY)
                    .withHtmlContent(html, null)
                    .toStream(output)
                    .run()
                output.toByteArray()
            }
        }.getOrElse { cause ->
            throw AdminDomainException(ErrorCode.ADMISSION_TICKET_GENERATION_FAILED, cause)
        }

    private fun extractFont(): File {
        val resource: InputStream = javaClass.getResourceAsStream(FONT_RESOURCE)
            ?: throw AdminDomainException(ErrorCode.ADMISSION_TICKET_GENERATION_FAILED)

        return resource.use { stream ->
            Files.createTempFile("admin-admission-ticket-", ".ttf").toFile().also { file ->
                file.deleteOnExit()
                file.outputStream().use(stream::copyTo)
            }
        }
    }
}
