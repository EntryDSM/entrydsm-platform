package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.command.UpdateArrivalCommand
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.DownloadLink
import hs.kr.entrydsm.admin.domain.model.ExamineeNumberIssueResult
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest

interface ReadApplicantUseCase {
    fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant>

    fun findById(applicantId: Long): Applicant
}

interface UpdateApplicantUseCase {
    fun updateArrival(command: UpdateArrivalCommand)

    fun updateStatus(command: UpdateApplicantStatusCommand)
}

interface IssueExamineeNumberUseCase {
    fun issueAll(): ExamineeNumberIssueResult
}

interface IssueAdmissionTicketUseCase {
    fun issue(applicantId: Long): DownloadLink
}

interface IssueApplicationDocumentUseCase {
    fun issue(applicantId: Long): DownloadLink
}
