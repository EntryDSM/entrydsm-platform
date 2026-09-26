package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPdfs
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import java.io.ByteArrayOutputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EssayPdfServiceTest {
    @Test
    fun `1차 합격자의 기존 PDF를 순서대로 하나의 PDF에 병합한다`() {
        val applicants = listOf(
            Applicant(id = 1, name = "홍/길동", examineeNumber = "11001", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 2, name = "홍/길동", examineeNumber = "11002", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 3, name = "불합격", status = ApplicantStatus.FIRST_FAIL),
        )
        val requested = mutableListOf<Pair<Long, String>>()
        val service = EssayPdfService(repository(applicants), ApplicationEssayPort { id, examineeNumber ->
            requested += id to examineeNumber
            val pdf = pdf(id.toInt())
            if (id == 1L) ApplicationEssayPdfs(pdf, null) else ApplicationEssayPdfs(pdf, pdf)
        })

        val output = ByteArrayOutputStream()
        service.writeTo(output)

        assertEquals(listOf(1L to "11001", 2L to "11002"), requested)
        Loader.loadPDF(output.toByteArray()).use { assertEquals(3, it.numberOfPages) }
    }

    @Test
    fun `대상이 없으면 빈 PDF를 반환한다`() {
        val output = ByteArrayOutputStream()
        EssayPdfService(repository(emptyList()), ApplicationEssayPort { _, _ -> error("호출되면 안 됨") }).writeTo(output)
        Loader.loadPDF(output.toByteArray()).use { assertEquals(0, it.numberOfPages) }
    }

    @Test
    fun `수험 번호가 없는 1차 합격자는 내보내지 않는다`() {
        val applicant = Applicant(id = 1, status = ApplicantStatus.FIRST_PASS)
        val service = EssayPdfService(repository(listOf(applicant)), ApplicationEssayPort { _, _ -> error("호출되면 안 됨") })

        assertThrows(IllegalStateException::class.java) { service.writeTo(ByteArrayOutputStream()) }
    }

    private fun repository(applicants: List<Applicant>) = object : ApplicantRepository {
        override fun findAll(filter: ApplicantFilter) = applicants.filter { filter.statuses.isEmpty() || it.status in filter.statuses }
        override fun search(filter: ApplicantFilter, pageRequest: hs.kr.entrydsm.admin.domain.model.PageRequest) = error("unused")
        override fun findById(applicantId: Long) = error("unused")
        override fun findDetailById(applicantId: Long) = error("unused")
        override fun save(applicant: Applicant) = error("unused")
        override fun saveAll(applicants: List<Applicant>) = error("unused")
    }

    private fun pdf(pageCount: Int): ByteArray = ByteArrayOutputStream().also { output ->
        PDDocument().use { document ->
            repeat(pageCount) { document.addPage(PDPage()) }
            document.save(output)
        }
    }.toByteArray()
}
