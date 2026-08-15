package hs.kr.entrydsm.admin.adapterin.web.dto.request

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class UpdateArrivalRequest(
    @field:NotNull
    val isSubmitted: Boolean?,
)

data class UpdateApplicantStatusRequest(
    @field:NotNull
    val status: ApplicantStatus?,
    val force: Boolean = false,
    @field:Size(max = 500)
    val reason: String? = null,
)
