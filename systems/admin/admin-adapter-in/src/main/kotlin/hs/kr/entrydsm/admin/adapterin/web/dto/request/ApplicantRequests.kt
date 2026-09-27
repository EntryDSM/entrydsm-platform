package hs.kr.entrydsm.admin.adapterin.web.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** 원서 원본(우편) 도착 여부를 정정한다. */
data class UpdateArrivalRequest(
    @field:NotNull
    @param:JsonProperty("isArrived")
    @get:JsonProperty("isArrived")
    val isArrived: Boolean?,
)

data class UpdateApplicantStatusRequest(
    @field:NotNull
    val status: ApplicantStatus?,
    val force: Boolean = false,
    @field:Size(max = 500)
    val reason: String? = null,
)
