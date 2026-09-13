package hs.kr.entrydsm.admin.domain.port.out

import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.model.ScorePolicy

/**
 * 지원자 조회와 전형 정보 저장을 함께 다룹니다.
 *
 * 원서 본문은 application 시스템이 소유하므로 조회만 하고, [save] 와 [saveAll] 은
 * 접수 번호·수험 번호·원서 도착 여부·전형 상태·성적 등 admin 이 소유한 값만 남깁니다.
 * 원서 본문에 해 둔 변경은 저장되지 않습니다.
 */
interface ApplicantRepository {
    fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant>

    fun findAll(filter: ApplicantFilter = ApplicantFilter()): List<Applicant>

    fun findById(applicantId: Long): Applicant?

    fun save(applicant: Applicant): Applicant

    fun saveAll(applicants: List<Applicant>): List<Applicant>
}

interface ScorePolicyRepository {
    fun findCurrent(): ScorePolicy?

    fun save(scorePolicy: ScorePolicy): ScorePolicy
}

interface AdmissionQuotaRepository {
    fun find(): AdmissionQuota?

    /** 지역 × 전형 정원 전체를 교체합니다. */
    fun save(admissionQuota: AdmissionQuota): AdmissionQuota
}

interface ExportJobRepository {
    fun findByExportJobId(exportJobId: String): ExportJob?

    fun save(exportJob: ExportJob): ExportJob
}

interface QuestionAnswerRepository {
    fun save(questionAnswer: QuestionAnswer): QuestionAnswer
}
