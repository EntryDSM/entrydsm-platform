package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.repository.ExportJobJpaRepository
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExportJob
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportJobPersistenceAdapterTest {

    /** 처리기는 저장 결과로 받은 작업의 필터로 지원자를 고른다. 비면 전체가 나간다. */
    @Test
    fun `저장 결과에 DB 에 남기지 않는 필터를 그대로 담는다`() {
        val filter = ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS))

        val saved = ExportJobPersistenceAdapter(echoingRepository()).save(
            ExportJob(
                exportJobId = "exp_1",
                type = ExportType.APPLICANT_LIST,
                status = ExportStatus.PENDING,
                filter = filter,
                totalCount = 10,
                processedCount = 7,
                createdAt = Instant.EPOCH,
            ),
        )

        assertEquals(filter, saved.filter)
        assertEquals(10, saved.totalCount)
        assertEquals(7, saved.processedCount)
    }

    /** DB 없이 save 만 흉내 낸다. 받은 엔티티를 그대로 돌려준다. */
    private fun echoingRepository(): ExportJobJpaRepository =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ExportJobJpaRepository::class.java),
        ) { _, method, args ->
            check(method.name == "save") { "unexpected call: ${method.name}" }
            args[0]
        } as ExportJobJpaRepository
}
