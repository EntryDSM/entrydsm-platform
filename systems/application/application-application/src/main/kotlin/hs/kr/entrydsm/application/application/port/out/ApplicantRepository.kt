package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.domain.model.Applicant

interface ApplicantRepository {
    fun save(applicant: Applicant): Applicant
    fun findById(id: Long): Applicant?
}
