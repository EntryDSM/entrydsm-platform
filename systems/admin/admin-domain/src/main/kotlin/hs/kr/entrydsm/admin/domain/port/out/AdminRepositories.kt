package hs.kr.entrydsm.admin.domain.port.out

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.DailyApplicantCount
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.Notice
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

    fun countAll(): Long

    fun countByAdmissionType(): Map<AdmissionType, Long>

    fun countByRegion(): Map<Region, Long>

    fun countBySubmittedDate(): List<DailyApplicantCount>
}

interface ScorePolicyRepository {
    fun findCurrent(): ScorePolicy?

    fun save(scorePolicy: ScorePolicy): ScorePolicy
}

interface ExportJobRepository {
    fun findByExportJobId(exportJobId: String): ExportJob?

    fun save(exportJob: ExportJob): ExportJob
}

interface NoticeRepository {
    fun save(notice: Notice): Notice
}

interface QuestionAnswerRepository {
    fun save(questionAnswer: QuestionAnswer): QuestionAnswer
}
