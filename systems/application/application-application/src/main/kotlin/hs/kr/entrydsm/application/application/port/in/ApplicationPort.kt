package hs.kr.entrydsm.application.application.port.`in`

import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
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
    fun getLanding(): LandingResult
}

