package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AdmissionQuotaTest {

    private val updatedAt = Instant.parse("2026-06-01T00:00:00Z")

    private fun quota(quotas: Map<Region, Map<AdmissionType, Int>>) =
        AdmissionQuota(quotas = quotas, updatedAt = updatedAt, updatedBy = "admin01")

    @Test
    fun `모든 지역과 전형이 채워지면 정원을 만들고 전형별 정원은 지역 합계다`() {
        val quota = quota(
            mapOf(
                Region.DAEJEON to mapOf(AdmissionType.GENERAL to 20, AdmissionType.MEISTER to 10, AdmissionType.SOCIAL to 5),
                Region.NATIONWIDE to mapOf(AdmissionType.GENERAL to 14, AdmissionType.MEISTER to 20, AdmissionType.SOCIAL to 0),
            ),
        )

        assertEquals(
            mapOf(AdmissionType.GENERAL to 34, AdmissionType.MEISTER to 30, AdmissionType.SOCIAL to 5),
            quota.byType,
        )
    }

    @Test
    fun `지역이 빠지면 정원을 거부한다`() {
        val exception = runCatching {
            quota(
                mapOf(
                    Region.DAEJEON to mapOf(AdmissionType.GENERAL to 20, AdmissionType.MEISTER to 10, AdmissionType.SOCIAL to 5),
                ),
            )
        }.exceptionOrNull()

        assertEquals(ErrorCode.INVALID_ADMISSION_QUOTA, (exception as AdminDomainException).errorCode)
    }

    @Test(expected = AdminDomainException::class)
    fun `전형이 빠지면 정원을 거부한다`() {
        quota(
            mapOf(
                Region.DAEJEON to mapOf(AdmissionType.GENERAL to 20, AdmissionType.MEISTER to 10),
                Region.NATIONWIDE to mapOf(AdmissionType.GENERAL to 14, AdmissionType.MEISTER to 20, AdmissionType.SOCIAL to 0),
            ),
        )
    }

    @Test(expected = AdminDomainException::class)
    fun `정원이 음수면 거부한다`() {
        quota(
            mapOf(
                Region.DAEJEON to mapOf(AdmissionType.GENERAL to -1, AdmissionType.MEISTER to 10, AdmissionType.SOCIAL to 5),
                Region.NATIONWIDE to mapOf(AdmissionType.GENERAL to 14, AdmissionType.MEISTER to 20, AdmissionType.SOCIAL to 0),
            ),
        )
    }
}
