package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPdfs
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EssayZipServiceTest {
    @Test
    fun `1차 합격자의 수험 번호를 넘겨 통합 문서 하나만 스트리밍한다`() {
        val applicants = listOf(
            Applicant(id = 1, name = "홍/길동", examineeNumber = "11001", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 2, name = "홍/길동", examineeNumber = "11002", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 3, name = "불합격", status = ApplicantStatus.FIRST_FAIL),
        )
        val requested = mutableListOf<Pair<Long, String>>()
        val service = EssayZipService(repository(applicants), ApplicationEssayPort { id, examineeNumber ->
            requested += id to examineeNumber
            if (id == 1L) ApplicationEssayPdfs("통합1".toByteArray(), null)
            else ApplicationEssayPdfs("통합2".toByteArray(), "통합2".toByteArray())
        })

        val output = ByteArrayOutputStream()
        service.writeTo(output)

        val entries = unzip(output.toByteArray())
        assertEquals(listOf(1L to "11001", 2L to "11002"), requested)
        assertEquals(
            listOf("1_홍_길동_자기소개서_및_학업계획서.pdf", "2_홍_길동_자기소개서_및_학업계획서.pdf"),
            entries.keys.toList(),
        )
        assertArrayEquals("통합2".toByteArray(), entries.getValue("2_홍_길동_자기소개서_및_학업계획서.pdf"))
    }

    @Test
    fun `대상이 없으면 빈 ZIP을 반환한다`() {
        val output = ByteArrayOutputStream()
        EssayZipService(repository(emptyList()), ApplicationEssayPort { _, _ -> error("호출되면 안 됨") }).writeTo(output)
        assertEquals(emptyMap<String, ByteArray>(), unzip(output.toByteArray()))
    }

    @Test
    fun `수험 번호가 없는 1차 합격자는 내보내지 않는다`() {
        val applicant = Applicant(id = 1, status = ApplicantStatus.FIRST_PASS)
        val service = EssayZipService(repository(listOf(applicant)), ApplicationEssayPort { _, _ -> error("호출되면 안 됨") })

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

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes())
                entry = zip.nextEntry
            }
        }
    }
}
