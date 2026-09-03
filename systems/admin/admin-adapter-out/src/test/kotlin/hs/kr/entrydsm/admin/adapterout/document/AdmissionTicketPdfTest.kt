package hs.kr.entrydsm.admin.adapterout.document

import hs.kr.entrydsm.admin.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.AdmissionTicket
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmissionTicketPdfTest {

    @Test
    fun `수험표 HTML을 PDF로 변환한다`() {
        val html = AdmissionTicketHtml.render(
            AdmissionTicket(
                admissionYear = 2027,
                receiptNumber = 2,
                examineeNumber = null,
                name = "홍길동",
                schoolName = "서울중학교",
                region = Region.NATIONWIDE,
                admissionType = AdmissionType.GENERAL,
            ),
        )

        val pdf = OpenHtmlToPdfAdapter().render(html)

        assertTrue(pdf.size > 1024)
        assertTrue(String(pdf.copyOfRange(0, 5)) == "%PDF-")

        // 눈으로 확인할 때 쓴다. bazel-testlogs/.../test.outputs/outputs.zip 에 담긴다.
        System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")?.let { directory ->
            File(directory, "admission-ticket.pdf").writeBytes(pdf)
        }
    }
}
