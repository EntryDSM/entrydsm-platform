package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import hs.kr.entrydsm.admin.domain.port.out.EssayPdfMergePort
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class EssayZipServiceTest {
    @Test
    fun `합격자의 통합 문서를 순서대로 병합기에 전달한다`() {
        val applicants = listOf(
            Applicant(id = 1, examineeNumber = "11001", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 2, examineeNumber = "11002", status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 3, status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 4, status = ApplicantStatus.FIRST_PASS),
            Applicant(id = 5, status = ApplicantStatus.FIRST_FAIL),
            Applicant(id = 6, status = ApplicantStatus.PENDING),
        )
        val documents = listOf(
            byteArrayOf(1),
            byteArrayOf(2, 3),
            byteArrayOf(4),
            null,
        )
        val requested = mutableListOf<Pair<Long, String>>()
        val output = ByteArrayOutputStream()
        var merged = false
        val service = EssayPdfService(repository(applicants), object : ApplicationEssayPort {
            override fun render(applicantId: Long, examineeNumber: String) = error("batch 호출 필요")
            override fun renderBatch(targets: List<Pair<Long, String>>): List<ByteArray?> {
                requested += targets
                return documents
            }
        }, EssayPdfMergePort { actual, stream ->
            assertSame(documents, actual)
            assertSame(output, stream)
            merged = true
        })

        assertEquals(4, service.writeTo(output))
        assertEquals(listOf(1L to "11001", 2L to "11002", 3L to "", 4L to ""), requested)
        assertEquals(true, merged)
    }

    @Test
    fun `대상이 없으면 빈 문서 목록을 병합기에 전달한다`() {
        var merged = false
        val service = EssayPdfService(repository(emptyList()), ApplicationEssayPort { _, _ -> error("호출되면 안 됨") },
            EssayPdfMergePort { documents, _ -> assertEquals(emptyList<ByteArray?>(), documents); merged = true })
        assertEquals(0, service.writeTo(ByteArrayOutputStream()))
        assertEquals(true, merged)
    }

    @Test
    fun `번호 전부 미발급과 혼합에서 공란 번호와 기존 순서 및 미발급 접수번호순을 유지한다`() {
        for (issued in listOf(false, true)) {
            val requested = mutableListOf<Pair<Long, String>>()
            val applicants = listOf(
                Applicant(id = 9, examineeNumber = if (issued) "11001" else "\t", status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 7, examineeNumber = " ", status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 4, examineeNumber = if (issued) " 11002 " else "", status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 3, status = ApplicantStatus.FIRST_PASS),
                Applicant(id = 2, examineeNumber = "11003", status = ApplicantStatus.FIRST_FAIL),
            )
            val service = EssayPdfService(repository(applicants), ApplicationEssayPort { id, value ->
                requested += id to value
                null
            }, EssayPdfMergePort { documents, _ -> assertEquals(4, documents.size) })
            assertEquals(4, service.writeTo(ByteArrayOutputStream()))
            assertEquals(if (issued) listOf(4L to " 11002 ", 9L to "11001", 3L to "", 7L to "")
                else listOf(3L to "", 4L to "", 7L to "", 9L to ""), requested)
        }
    }

    @Test
    fun `병합 실패는 성공 처리하지 않고 원래 예외를 전달한다`() {
        val failure = IllegalStateException("merge failed")
        val service = EssayPdfService(repository(listOf(Applicant(id = 1, status = ApplicantStatus.FIRST_PASS))),
            ApplicationEssayPort { _, _ -> null }, EssayPdfMergePort { _, _ -> throw failure })
        assertSame(failure, assertThrows(IllegalStateException::class.java) { service.writeTo(ByteArrayOutputStream()) })
    }

    private fun repository(applicants: List<Applicant>) = object : ApplicantRepository {
        override fun findAll(filter: ApplicantFilter) = applicants.filter { filter.statuses.isEmpty() || it.status in filter.statuses }
        override fun search(filter: ApplicantFilter, pageRequest: hs.kr.entrydsm.admin.domain.model.PageRequest) = error("unused")
        override fun findById(applicantId: Long) = error("unused")
        override fun findDetailById(applicantId: Long) = error("unused")
        override fun save(applicant: Applicant) = error("unused")
        override fun saveAll(applicants: List<Applicant>) = error("unused")
    }
}
