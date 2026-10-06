package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AdmissionQuotaTest {

    private val updatedAt = Instant.parse("2026-06-01T00:00:00Z")

    private fun quota(quotas: Map<AdmissionType, Int>) =
        AdmissionQuota(quotas = quotas, updatedAt = updatedAt, updatedBy = "admin01")

    @Test
    fun `모든 전형이 채워지면 정원을 만든다`() {
        val quota = quota(
            mapOf(
                AdmissionType.GENERAL to 20,
                AdmissionType.MEISTER to 10,
                AdmissionType.SOCIAL to 5,
            ),
        )

        assertEquals(
            mapOf(AdmissionType.GENERAL to 20, AdmissionType.MEISTER to 10, AdmissionType.SOCIAL to 5),
            quota.quotas,
        )
    }

    @Test
    fun `배수를 곱한 정원은 올림한다`() {
        val quota = quota(
            mapOf(
                AdmissionType.GENERAL to 20,
                AdmissionType.MEISTER to 10,
                AdmissionType.SOCIAL to 5,
            ),
        )

        assertEquals(
            mapOf(
                AdmissionType.GENERAL to 30,
                AdmissionType.MEISTER to 15,
                AdmissionType.SOCIAL to 8,
            ),
            quota.scaled(1.5),
        )
    }

    @Test
    fun `전형이 빠지면 정원을 거부한다`() {
        val exception = runCatching {
            quota(
                mapOf(
                    AdmissionType.GENERAL to 20,
                    AdmissionType.MEISTER to 10,
                ),
            )
        }.exceptionOrNull()

        assertEquals(ErrorCode.INVALID_ADMISSION_QUOTA, (exception as AdminDomainException).errorCode)
    }

    @Test(expected = AdminDomainException::class)
    fun `정원이 음수면 거부한다`() {
        quota(
            mapOf(
                AdmissionType.GENERAL to -1,
                AdmissionType.MEISTER to 10,
                AdmissionType.SOCIAL to 5,
            ),
        )
    }
}
