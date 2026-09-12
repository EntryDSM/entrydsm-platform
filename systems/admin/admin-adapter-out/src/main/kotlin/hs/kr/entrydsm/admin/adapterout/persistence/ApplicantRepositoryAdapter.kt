package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantScreeningJpaEntity
import hs.kr.entrydsm.admin.adapterout.grpc.ApplicantRecord
import hs.kr.entrydsm.admin.adapterout.grpc.GrpcApplicantDataAdapter
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantScreeningJpaRepository
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ApplicantScore
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 원서 본문은 application 에서 읽고 전형 정보는 admin DB 에서 읽어 하나로 합칩니다.
 *
 * 필터와 페이지는 메모리에서 처리합니다. 두 저장소에 걸친 조건이라 한쪽 쿼리로 내릴 수
 * 없고, 회차 지원자가 수백 명 수준이라 전체를 받아도 충분하기 때문입니다.
 *
 * ponytail: 단건 조회도 회차 전체를 한 번 받아 온다. 수험표 발급처럼 한 명만 필요한
 * 호출이 잦아지면 계약에 단건 조회 RPC 를 더한다.
 */
@Component
class ApplicantRepositoryAdapter(
    private val applicantDataAdapter: GrpcApplicantDataAdapter,
    private val screeningRepository: ApplicantScreeningJpaRepository,
    private val screeningInitializer: ApplicantScreeningInitializer,
) : ApplicantRepository {

    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> {
        val matched = findAll(filter)
        val fromIndex = (pageRequest.normalizedPage - 1) * pageRequest.normalizedSize

        return Page(
            items = matched.drop(fromIndex).take(pageRequest.normalizedSize),
            page = pageRequest.normalizedPage,
            size = pageRequest.normalizedSize,
            totalElements = matched.size.toLong(),
        )
    }

    override fun findAll(filter: ApplicantFilter): List<Applicant> =
        loadAll().filter(filter::matches)

    override fun findById(applicantId: Long): Applicant? =
        loadAll().firstOrNull { it.id == applicantId }

    @Transactional
    override fun save(applicant: Applicant): Applicant {
        persist(listOf(applicant))
        return applicant
    }

    @Transactional
    override fun saveAll(applicants: List<Applicant>): List<Applicant> {
        persist(applicants)
        return applicants
    }

    /** 원서 본문은 application 이 소유하므로 전형 정보만 남깁니다. */
    private fun persist(applicants: List<Applicant>) {
        if (applicants.isEmpty()) return

        val existing = screeningRepository.findAllById(applicants.map { it.id })
            .associateBy { it.applicantId }
        val entities = applicants.map { applicant ->
            (existing[applicant.id] ?: ApplicantScreeningJpaEntity(applicantId = applicant.id))
                .apply { applyFrom(applicant) }
        }
        screeningRepository.saveAll(entities)
    }

    private fun loadAll(): List<Applicant> {
        val records = applicantDataAdapter.findAllSubmitted()
        val screenings = screeningInitializer.ensureFor(records)

        return records
            .mapNotNull { record -> screenings[record.applicantId]?.let(record::merge) }
            .sortedBy { it.receiptNumber }
    }

    /**
     * 성적은 admin 이 성적 정책으로 다시 매긴 값이 있으면 그것을, 없으면 application 이
     * 산출한 값을 씁니다.
     */
    private fun ApplicantRecord.merge(screening: ApplicantScreeningJpaEntity): Applicant = Applicant(
        id = applicantId,
        name = name,
        birthDate = birthDate,
        phoneNumber = phoneNumber,
        region = region,
        admissionType = admissionType,
        graduationStatus = graduationStatus,
        schoolName = schoolName,
        submittedAt = submittedAt,
        receiptNumber = screening.receiptNumber,
        examineeNumber = screening.examineeNumber,
        isSubmitted = screening.documentReceived,
        status = screening.status,
        score = screening.score() ?: score?.let {
            ApplicantScore(
                subjectScore = it.subjectScore,
                attendanceScore = it.attendanceScore,
                volunteerScore = it.volunteerScore,
                totalScore = it.totalScore,
            )
        },
        updatedAt = screening.updatedAt ?: updatedAt,
    )
}
