package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantArrivalPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.DistancePort
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminApplicationModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun collectsCompetitionGenderAndResidenceStatistics() {
        val applicants = listOf(
            applicant(1, AdmissionType.GENERAL, Gender.MALE, Region.DAEJEON, "대전광역시 유성구"),
            applicant(2, AdmissionType.GENERAL, Gender.FEMALE, Region.NATIONWIDE, "충청남도 천안시"),
            applicant(3, AdmissionType.GENERAL, Gender.MALE, Region.DAEJEON, "세종특별자치시 한누리대로"),
        )
        val quota = AdmissionQuota(
            quotas = Region.entries.associateWith { region ->
                AdmissionType.entries.associateWith { type ->
                    if (region == Region.DAEJEON && type == AdmissionType.GENERAL) 2 else 0
                }
            },
            updatedAt = Instant.EPOCH,
            updatedBy = "test",
        )
        val service = StatisticsService(
            applicantRepository = repository(ApplicantRepository::class.java, "findAll" to applicants),
            admissionQuotaRepository = repository(AdmissionQuotaRepository::class.java, "find" to quota),
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        val result = service.collect(
            setOf(
                StatisticsMetric.COMPETITION_RATE,
                StatisticsMetric.GENDER_RATIO,
                StatisticsMetric.REGION_STATUS,
            ),
        )

        assertEquals(1.5, result.competitionRate?.get(AdmissionType.GENERAL))
        assertEquals(3L, result.genderRatio?.total)
        assertEquals(0.667, result.genderRatio?.maleRatio)
        assertEquals(mapOf(Gender.MALE to 2L, Gender.FEMALE to 1L), result.genderRatio?.byGender)
        assertEquals(mapOf("LOCAL" to 2L, "NATIONWIDE" to 1L), result.regionStatus?.byScope)
        assertEquals(1L, result.regionStatus?.byRegion?.get(ResidenceRegion.DAEJEON))
        assertEquals(1L, result.regionStatus?.byRegion?.get(ResidenceRegion.CHUNGNAM))
        assertEquals(1L, result.regionStatus?.byRegion?.get(ResidenceRegion.SEJONG))
    }

    @Test
    fun doesNotSaveAnyNumberWhenOneDistanceLookupFailsAndSkipsAlreadyIssuedApplicant() {
        val applicants = listOf(
            Applicant(1L, admissionType = AdmissionType.MEISTER, region = Region.DAEJEON, address = "주소1", isArrived = true),
            Applicant(2L, admissionType = AdmissionType.MEISTER, region = Region.DAEJEON, address = "주소2", isArrived = true),
            Applicant(
                3L,
                admissionType = AdmissionType.MEISTER,
                region = Region.DAEJEON,
                address = "주소3",
                isArrived = true,
                examineeNumber = "11001",
            ),
        )
        val repository = FakeApplicantRepository(applicants)
        val requested = mutableListOf<String>()
        val service = ApplicantService(
            applicantRepository = repository,
            applicantArrivalPort = ApplicantArrivalPort { _, _ -> },
            distancePort = DistancePort { address ->
                requested += address
                if (address == "주소2") error("maps failed") else 100L
            },
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

        runCatching { service.issueAll() }

        assertEquals(listOf("주소1", "주소2"), requested)
        assertTrue(repository.saved.isEmpty())
    }

    private fun applicant(
        id: Long,
        type: AdmissionType,
        gender: Gender,
        region: Region,
        address: String,
    ) = Applicant(id = id, admissionType = type, gender = gender, region = region, address = address)

    private fun <T> repository(type: Class<T>, response: Pair<String, Any>): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, _ ->
            if (method.name == response.first) response.second else error("unexpected call: ${method.name}")
        } as T

    private class FakeApplicantRepository(private val applicants: List<Applicant>) : ApplicantRepository {
        val saved = mutableListOf<Applicant>()

        override fun search(filter: ApplicantFilter, pageRequest: PageRequest) = Page<Applicant>(emptyList(), 1, 20, 0L)
        override fun findAll(filter: ApplicantFilter) = applicants
        override fun findById(applicantId: Long) = applicants.find { it.id == applicantId }
        override fun findDetailById(applicantId: Long): ApplicantDetail? = null
        override fun save(applicant: Applicant) = applicant.also(saved::add)
        override fun saveAll(applicants: List<Applicant>) = applicants.also(saved::addAll)
    }
}
