package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import java.time.LocalDate
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ApplicantJpaRepository : JpaRepository<ApplicantJpaEntity, Long> {
    fun findByAccountId(accountId: Long): ApplicantJpaEntity?

    /**
     * 목록에 쓰는 열만 한 번에 읽습니다.
     *
     * 엔티티로 읽으면 안 됩니다. `middle_school_infos`·`academic_records` 는 역방향
     * `@OneToOne(mappedBy=...)` 이고 nullable 이라 Hibernate 가 프록시를 못 만들어,
     * 쓰든 안 쓰든 지원자마다 SELECT 를 더 냅니다(`ged_scores` 까지 합쳐 한 명당 다섯 번).
     * 회차 전체를 부르는 목록에서는 이게 곧 지원자 수에 비례하는 질의가 됩니다.
     */
    @Query(
        """
        select a.id as id, a.accountId as accountId, a.name as name, m.schoolName as schoolName,
               a.region as region, a.admissionType as admissionType, a.photoFileId as photoFileId,
               a.birthdate as birthdate, a.phoneNumber as phoneNumber,
               a.graduationType as graduationType, a.totalScore as totalScore,
               a.status as status, a.submittedAt as submittedAt
          from ApplicantJpaEntity a
          left join a.middleSchoolInfo m
         where a.status in :statuses
         order by a.id
        """,
    )
    fun findSummariesByStatusIn(statuses: Set<ApplicantStatus>): List<ApplicantSummaryRow>
}

/** 이름으로 맞춰지는 조회 결과라 열 순서가 어긋나도 값이 바뀌지 않습니다. */
interface ApplicantSummaryRow {
    val id: Long
    val accountId: Long
    val name: String?
    val schoolName: String?
    val region: Region?
    val admissionType: AdmissionType?
    val photoFileId: String?
    val birthdate: LocalDate?
    val phoneNumber: String?
    val graduationType: GraduationType?
    val totalScore: Double?
    val status: ApplicantStatus
    val submittedAt: LocalDateTime?
}
