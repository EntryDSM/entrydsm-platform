package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.command.UpdateArrivalCommand
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExamineeNumberIssueResult
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest

interface ReadApplicantUseCase {
    fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant>

    fun findDetail(applicantId: Long): ApplicantDetail
}

interface UpdateApplicantUseCase {
    fun updateArrival(command: UpdateArrivalCommand)

    fun updateStatus(command: UpdateApplicantStatusCommand)
}

interface IssueExamineeNumberUseCase {
    fun issueAll(): ExamineeNumberIssueResult
}
