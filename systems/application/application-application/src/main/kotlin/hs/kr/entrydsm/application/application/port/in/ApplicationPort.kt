package hs.kr.entrydsm.application.application.port.`in`

import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateApplicantArrivalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult

interface ApplicationPort {
    fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult
    fun updateType(command: UpdateTypeCommand)
    fun updatePersonal(command: UpdatePersonalCommand)
    fun updateFamily(command: UpdateFamilyCommand)
    fun updateMiddleSchool(command: UpdateMiddleSchoolCommand)
    fun updateIntroduction(command: UpdateIntroductionCommand)
    fun updateStudyPlan(command: UpdateStudyPlanCommand)
    fun submit(command: SubmitApplicationCommand)
    fun updateArrival(command: UpdateApplicantArrivalCommand): ApplicationSnapshotResult =
        throw UnsupportedOperationException("updateArrival is not implemented")
    fun getLanding(accountId: Long?): LandingResult
    fun findByAccountId(accountId: Long): ApplicationSnapshotResult?
    fun findApplicant(applicantId: Long): ApplicantResult?
    /** 요강 <서식 1> 입학원서를 찍는 데 쓰는 원서 내용. 그 계정의 원서가 없으면 null. */
    fun findApplicationForm(accountId: Long): ApplicationFormResult?
    fun findApplicationForms(accountIds: List<Long>): List<ApplicationFormResult> =
        accountIds.mapNotNull(::findApplicationForm)

    /** 제출·심사·완료 상태의 원서 전체. 작성 중이거나 취소된 원서는 지원자가 아니다. */
    fun listApplicants(): List<ApplicantResult>
    fun cancel(accountId: Long, reason: String?): ApplicationSnapshotResult
}

