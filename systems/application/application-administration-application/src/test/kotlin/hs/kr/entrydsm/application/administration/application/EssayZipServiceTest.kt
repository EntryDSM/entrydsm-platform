package hs.kr.entrydsm.application.administration.application

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
import java.io.OutputStream
import org.junit.Test

class EssayZipServiceTest {
    @Test
    fun `1차 합격자의 기존 PDF를 순서대로 하나의 PDF에 병합한다`() {
        val applicants = listOf(
            Applicant(id = 1, name = "홍/길동", examineeNumber = "11001", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 2, name = "홍/길동", examineeNumber = "11002", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 3, name = "불합격", status = ApplicantStatus.FIRST_FAIL),
        )
        val requested = mutableListOf<Pair<Long, String>>()
        val service = EssayPdfService(repository(applicants), object : ApplicationEssayPort {
            override fun render(applicantId: Long, examineeNumber: String) = error("batch 호출 필요")

            override fun renderBatch(targets: List<Pair<Long, String>>): List<ApplicationEssayPdfs> {
                requested += targets
                return targets.map { (id, _) ->
                    val pdf = pdf(id.toInt())
                    if (id == 1L) ApplicationEssayPdfs(pdf, null) else ApplicationEssayPdfs(pdf, pdf)
                }
            }
        })

        val output = ByteArrayOutputStream()
        service.writeTo(output)

        assertEquals(listOf(1L to "11001", 2L to "11002"), requested)
        Loader.loadPDF(output.toByteArray()).use { assertEquals(5, it.numberOfPages) }
    }

    @Test
    fun `대상이 없으면 빈 PDF를 반환한다`() {
        val output = ByteArrayOutputStream()
        EssayPdfService(repository(emptyList()), ApplicationEssayPort { _, _ -> error("호출되면 안 됨") }).writeTo(output)
        Loader.loadPDF(output.toByteArray()).use { assertEquals(0, it.numberOfPages) }
    }

    @Test
    fun `학업계획서만 있는 지원자의 문서도 포함하고 문서가 없는 합격자도 처리 건수에 포함한다`() {
        val applicants = listOf(
            Applicant(id = 1, status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 2, status = ApplicantStatus.FIRST_PASS),
        )
        val output = ByteArrayOutputStream()
        val service = EssayPdfService(repository(applicants), ApplicationEssayPort { id, _ ->
            if (id == 1L) ApplicationEssayPdfs(null, pdf(2)) else ApplicationEssayPdfs(null, null)
        })

        assertEquals(2, service.writeTo(output))
        Loader.loadPDF(output.toByteArray()).use { assertEquals(2, it.numberOfPages) }
    }

    @Test
    fun `수험 번호가 전부 또는 일부 없어도 합격자 전원을 기존 순서와 미발급 접수번호 순으로 출력한다`() {
        for (number in listOf(null, "11002")) {
            val requested = mutableListOf<Pair<Long, String>>()
            val applicants = listOf(
                Applicant(id = 9, examineeNumber = number, status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 7, status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 4, examineeNumber = number?.let { "11001" }, status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 3, status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 2, examineeNumber = "11003", status = ApplicantStatus.FIRST_FAIL),
            )
            val service = EssayPdfService(repository(applicants), ApplicationEssayPort { id, value ->
                requested += id to value
                ApplicationEssayPdfs(pdf(1), pdf(1))
            })
            val output = ByteArrayOutputStream()
            assertEquals(4, service.writeTo(output))
            assertEquals(if (number == null) listOf(3L to "", 4L to "", 7L to "", 9L to "")
                else listOf(4L to "11001", 9L to "11002", 3L to "", 7L to ""), requested)
            Loader.loadPDF(output.toByteArray()).use { assertEquals(8, it.numberOfPages) }
        }
    }

    @Test
    fun `잘못된 PDF와 출력 스트림 장애는 계속 실패한다`() {
        val applicant = Applicant(id = 1, status = ApplicantStatus.FIRST_PASS)
        assertThrows(Exception::class.java) {
            EssayPdfService(repository(listOf(applicant)), ApplicationEssayPort { _, _ ->
                ApplicationEssayPdfs("broken pdf".toByteArray(), null)
            }).writeTo(ByteArrayOutputStream())
        }
        assertThrows(Exception::class.java) {
            EssayPdfService(repository(listOf(applicant)), ApplicationEssayPort { _, _ ->
                ApplicationEssayPdfs(pdf(1), null)
            }).writeTo(object : OutputStream() { override fun write(value: Int) { error("storage failed") } })
        }
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
