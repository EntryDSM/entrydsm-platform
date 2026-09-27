package hs.kr.entrydsm.configuration.adapterout

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicket
import hs.kr.entrydsm.configuration.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.configuration.domain.document.Applicant
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class AdmissionTicketPdfTest {

    @Test
    fun `수험표 HTML을 사진과 한글 폰트를 넣어 PDF로 바꾼다`() {
        val html = AdmissionTicketHtml.render(
            AdmissionTicket.of(
                admissionYear = 2027,
                applicantId = 12,
                applicant = Applicant(10, "홍길동", "대덕중학교", Applicant.Region.DAEJEON, Applicant.AdmissionType.MEISTER, "photo_a"),
                examineeNumber = "100001",
                photo = AdmissionTicket.Photo("image/png", png()),
            ),
        )

        val pdf = OpenHtmlToPdfAdapter().render(html)

        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/outputs.zip 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { File(it, "admission-ticket.pdf").writeBytes(pdf) }
    }

    @Test
    fun `읽을 수 없는 사진은 빈 칸으로 두고 PDF는 만든다`() {
        val html = AdmissionTicketHtml.render(
            AdmissionTicket.of(
                admissionYear = 2027,
                applicantId = 12,
                applicant = Applicant(10, "홍길동", null, null, null, "photo_a"),
                examineeNumber = null,
                photo = AdmissionTicket.Photo("image/webp", "RIFF0000WEBPVP8 ".toByteArray()),
            ),
        )

        val pdf = OpenHtmlToPdfAdapter().render(html)

        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()
}
