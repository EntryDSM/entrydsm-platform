package hs.kr.entrydsm.admin.domain.port.out

import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.model.ScorePolicy

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
