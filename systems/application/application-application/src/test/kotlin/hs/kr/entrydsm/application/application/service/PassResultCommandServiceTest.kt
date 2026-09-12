package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.port.`in`.command.AnnouncePassResultsCommand
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.PassResultRepository
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.PassResult
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PassResultCommandServiceTest {

    @Test
    fun storesAnnouncedResults() {
        val repository = FakePassResultRepository()
        val service = PassResultCommandService(FakeApplicantRepository(setOf(1L, 2L)), repository)

        val applied = service.announce(
            AnnouncePassResultsCommand(
                listOf(
                    passResult(1L, ResultType.DOCUMENT, PassResultStatus.PASS),
                    passResult(2L, ResultType.DOCUMENT, PassResultStatus.FAIL),
                ),
            ),
        )

        assertEquals(2, applied)
        assertEquals(2, repository.stored.size)
    }

    @Test
    fun rejectsUnknownApplicantWithoutStoringAnything() {
        val repository = FakePassResultRepository()
        val service = PassResultCommandService(FakeApplicantRepository(setOf(1L)), repository)

        assertThrows(ApplicantNotFoundException::class.java) {
            service.announce(
                AnnouncePassResultsCommand(
                    listOf(
                        passResult(1L, ResultType.FINAL, PassResultStatus.PASS),
                        passResult(99L, ResultType.FINAL, PassResultStatus.PASS),
                    ),
                ),
            )
        }

        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun rejectsDuplicateEntriesForTheSameStage() {
        val repository = FakePassResultRepository()
        val service = PassResultCommandService(FakeApplicantRepository(setOf(1L)), repository)

        assertThrows(IllegalArgumentException::class.java) {
            service.announce(
                AnnouncePassResultsCommand(
                    listOf(
                        passResult(1L, ResultType.FINAL, PassResultStatus.PASS),
                        passResult(1L, ResultType.FINAL, PassResultStatus.FAIL),
                    ),
                ),
            )
        }

        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun rejectsPendingAsAnnouncement() {
        assertThrows(IllegalArgumentException::class.java) {
            passResult(1L, ResultType.FINAL, PassResultStatus.PENDING)
        }
    }

    private fun passResult(
        applicantId: Long,
        resultType: ResultType,
        result: PassResultStatus,
    ): PassResult = PassResult(
        applicantId = applicantId,
        resultType = resultType,
        result = result,
        processedBy = 7L,
        processedAt = LocalDateTime.of(2026, 11, 1, 9, 0),
    )

    private class FakePassResultRepository : PassResultRepository {
        val stored = mutableListOf<PassResult>()

        override fun upsertAll(results: List<PassResult>): Int {
            stored += results
            return results.size
        }
    }

    private class FakeApplicantRepository(
        private val ids: Set<Long>,
    ) : ApplicantRepository {
        override fun save(applicant: Applicant): Applicant = applicant
        override fun findById(id: Long): Applicant? = null
        override fun findByAccountId(accountId: Long): Applicant? = null
        override fun existingIds(ids: Collection<Long>): Set<Long> =
            ids.filterTo(mutableSetOf()) { it in this.ids }
    }
}
