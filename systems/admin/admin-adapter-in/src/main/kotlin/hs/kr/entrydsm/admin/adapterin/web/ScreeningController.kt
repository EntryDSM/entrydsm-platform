package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.request.EvaluateScreeningRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.request.UpdateScorePolicyRequest
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScorePolicyResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScreeningResultResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.StatisticsResponse
import hs.kr.entrydsm.admin.domain.command.EvaluateScreeningCommand
import hs.kr.entrydsm.admin.domain.command.UpdateScorePolicyCommand
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.ScoreWeights
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFinalScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadScorePolicyUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateScorePolicyUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ScreeningController(
    private val readScorePolicyUseCase: ReadScorePolicyUseCase,
    private val updateScorePolicyUseCase: UpdateScorePolicyUseCase,
    private val evaluateFirstScreeningUseCase: EvaluateFirstScreeningUseCase,
    private val evaluateFinalScreeningUseCase: EvaluateFinalScreeningUseCase,
    private val readStatisticsUseCase: ReadStatisticsUseCase,
) {

    @GetMapping(AdminEndpointPaths.SCORE_POLICY)
    fun findCurrentPolicy(): ResponseEntity<ApiResponse<ScorePolicyResponse>> =
        ResponseEntity.ok(ApiResponse(data = readScorePolicyUseCase.findCurrent().toResponse()))

    @PutMapping(AdminEndpointPaths.SCORE_POLICY)
    fun updatePolicy(
        @RequestHeader(USER_ID_HEADER) userId: String,
        @Valid @RequestBody request: UpdateScorePolicyRequest,
    ): ResponseEntity<Unit> {
        val weights = request.weights!!
        updateScorePolicyUseCase.update(
            UpdateScorePolicyCommand(
                weights = ScoreWeights(
                    subject = weights.subject!!,
                    attendance = weights.attendance!!,
                    volunteer = weights.volunteer!!,
                ),
                roundingScale = request.roundingScale!!,
                recalculate = request.recalculate,
                updatedBy = userId,
            ),
        )
        return ResponseEntity.accepted().build()
    }

    @PostMapping(AdminEndpointPaths.FIRST_SCREENING_RESULTS)
    fun evaluateFirst(
        @RequestBody(required = false) request: EvaluateScreeningRequest?,
    ): ResponseEntity<ApiResponse<ScreeningResultResponse>> =
        ResponseEntity.ok(
            ApiResponse(
                data = evaluateFirstScreeningUseCase
                    .evaluateFirst(EvaluateScreeningCommand(dryRun = request?.dryRun ?: false))
                    .toResponse(),
            ),
        )

    @PostMapping(AdminEndpointPaths.FINAL_SCREENING_RESULTS)
    fun evaluateFinal(
        @RequestBody(required = false) request: EvaluateScreeningRequest?,
    ): ResponseEntity<ApiResponse<ScreeningResultResponse>> =
        ResponseEntity.ok(
            ApiResponse(
                data = evaluateFinalScreeningUseCase
                    .evaluateFinal(EvaluateScreeningCommand(dryRun = request?.dryRun ?: false))
                    .toResponse(),
            ),
        )

    @GetMapping(AdminEndpointPaths.STATISTICS)
    fun statistics(
        @RequestParam metrics: Set<StatisticsMetric>,
    ): ResponseEntity<ApiResponse<StatisticsResponse>> =
        ResponseEntity.ok(ApiResponse(data = readStatisticsUseCase.collect(metrics).toResponse()))
}
