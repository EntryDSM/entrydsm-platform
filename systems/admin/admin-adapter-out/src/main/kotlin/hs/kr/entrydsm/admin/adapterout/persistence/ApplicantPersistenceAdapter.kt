package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantSpecifications
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.DailyApplicantCount
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.time.ZoneId
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

private val KOREA_ZONE = ZoneId.of("Asia/Seoul")

@Component
class ApplicantPersistenceAdapter(
    private val applicantJpaRepository: ApplicantJpaRepository,
) : ApplicantRepository {

    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> {
        val springPage = applicantJpaRepository.findAll(
            ApplicantSpecifications.of(filter),
            org.springframework.data.domain.PageRequest.of(
                pageRequest.normalizedPage - 1,
                pageRequest.normalizedSize,
                Sort.by(Sort.Direction.ASC, "receiptNumber"),
            ),
        )

        return Page(
            items = springPage.content.map(ApplicantJpaEntity::toDomain),
            page = pageRequest.normalizedPage,
            size = pageRequest.normalizedSize,
            totalElements = springPage.totalElements,
        )
    }

    override fun findAll(filter: ApplicantFilter): List<Applicant> =
        applicantJpaRepository
            .findAll(ApplicantSpecifications.of(filter), Sort.by(Sort.Direction.ASC, "receiptNumber"))
            .map(ApplicantJpaEntity::toDomain)

    override fun findById(applicantId: Long): Applicant? =
        applicantJpaRepository.findById(applicantId).orElse(null)?.toDomain()

    override fun save(applicant: Applicant): Applicant =
        applicantJpaRepository.save(ApplicantJpaEntity.from(applicant)).toDomain()

    override fun saveAll(applicants: List<Applicant>): List<Applicant> =
        applicantJpaRepository
            .saveAll(applicants.map(ApplicantJpaEntity::from))
            .map(ApplicantJpaEntity::toDomain)

    override fun countAll(): Long = applicantJpaRepository.count()

    // ponytail: 통계 집계를 메모리에서 돈다. 한 회차 지원자가 수천 명 규모라 충분하다.
    // 만 단위로 커지면 GROUP BY 쿼리로 내린다.
    override fun countByAdmissionType(): Map<AdmissionType, Long> =
        findAll().groupingBy { it.admissionType }.eachCount().mapValues { it.value.toLong() }

    override fun countByRegion(): Map<Region, Long> =
        findAll().groupingBy { it.region }.eachCount().mapValues { it.value.toLong() }

    override fun countBySubmittedDate(): List<DailyApplicantCount> =
        findAll()
            .mapNotNull { it.submittedAt }
            .groupingBy { it.atZone(KOREA_ZONE).toLocalDate() }
            .eachCount()
            .map { (date, count) -> DailyApplicantCount(date, count.toLong()) }
            .sortedBy { it.date }
}
