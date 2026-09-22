package hs.kr.entrydsm.admin.application

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
import org.junit.Test

class EssayZipServiceTest {
    @Test
    fun `작성된 항목만 안전한 중복 없는 이름으로 스트리밍한다`() {
        val applicants = listOf(Applicant(id = 1, name = "홍/길동"), Applicant(id = 2, name = "홍/길동"))
        val service = EssayZipService(repository(applicants), ApplicationEssayPort {
            if (it == 1L) ApplicationEssayPdfs("소개".toByteArray(), null)
            else ApplicationEssayPdfs("소개2".toByteArray(), "계획".toByteArray())
        })

        val output = ByteArrayOutputStream()
        service.writeTo(output)

        val entries = unzip(output.toByteArray())
        assertEquals(listOf("1_홍_길동_자기소개서.pdf", "2_홍_길동_자기소개서.pdf", "2_홍_길동_학업계획서.pdf"), entries.keys.toList())
        assertArrayEquals("계획".toByteArray(), entries.getValue("2_홍_길동_학업계획서.pdf"))
    }

    @Test
    fun `대상이 없으면 빈 ZIP을 반환한다`() {
        val output = ByteArrayOutputStream()
        EssayZipService(repository(emptyList()), ApplicationEssayPort { error("호출되면 안 됨") }).writeTo(output)
        assertEquals(emptyMap<String, ByteArray>(), unzip(output.toByteArray()))
    }

    private fun repository(applicants: List<Applicant>) = object : ApplicantRepository {
        override fun findAll(filter: ApplicantFilter) = applicants
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
