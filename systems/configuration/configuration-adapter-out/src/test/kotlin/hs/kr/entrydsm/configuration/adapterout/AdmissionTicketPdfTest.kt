package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.configuration.domain.document.Applicant
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

class AdmissionTicketPdfTest {

    @Test
    fun `수험표 HTML을 사진과 한글 폰트를 넣어 PDF로 바꾼다`() {
        val html = AdmissionTicketHtml.render(
            admissionYear = 2027,
            applicant = Applicant(10, "홍길동", "대덕중학교", Applicant.Region.DAEJEON, Applicant.AdmissionType.MEISTER, "photo_a"),
            photoDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png()),
        )

        val pdf = OpenHtmlToPdfAdapter().render(html)

        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/outputs.zip 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "admission-ticket.pdf").writeBytes(pdf) }
    }

    @Test
    fun `읽을 수 없는 사진은 빈 칸으로 두고 PDF는 만든다`() {
        val html = AdmissionTicketHtml.render(
            admissionYear = 2027,
            applicant = Applicant(10, "홍길동", null, null, null, "photo_a"),
            photoDataUri = "data:image/webp;base64," + Base64.getEncoder().encodeToString("RIFF0000WEBPVP8 ".toByteArray()),
        )

        val pdf = OpenHtmlToPdfAdapter().render(html)

        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()
}
