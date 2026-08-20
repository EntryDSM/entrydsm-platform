package hs.kr.entrydsm.observability.adapterin.web.dto.request

import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.LogSource
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class ClientLogCollectRequest(
    @field:NotBlank val sessionId: String,
    @field:NotEmpty val logs: List<@Valid ClientLogItemRequest>,
)

data class ClientLogItemRequest(
    @field:NotNull val level: LogLevel,
    @field:NotNull val source: LogSource,
    @field:NotBlank val message: String,
    val stack: String?,
    @field:NotBlank val pageUrl: String,
    @field:NotNull val occurredAt: Instant,
)
