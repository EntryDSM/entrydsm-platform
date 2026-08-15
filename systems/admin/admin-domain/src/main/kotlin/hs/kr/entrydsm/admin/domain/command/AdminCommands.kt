package hs.kr.entrydsm.admin.domain.command

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ScoreWeights

/**
 * 원서 도착 여부를 정정합니다.
 */
data class UpdateArrivalCommand(
    val applicantId: Long,
    val isSubmitted: Boolean,
)

/**
 * 지원자 상태를 개별 정정합니다.
 *
 * @property force 정상 전이 흐름을 벗어나 강제로 바꿀지 여부
 * @property reason 정정 사유. 강제 변경일 때는 반드시 있어야 한다
 */
data class UpdateApplicantStatusCommand(
    val applicantId: Long,
    val status: ApplicantStatus,
    val force: Boolean = false,
    val reason: String? = null,
)

/**
 * 성적 산출 정책을 교체합니다.
 *
 * @property recalculate 정책 반영 후 기존 지원자 점수를 다시 계산할지 여부
 */
data class UpdateScorePolicyCommand(
    val weights: ScoreWeights,
    val roundingScale: Int,
    val recalculate: Boolean = false,
    val updatedBy: String,
)

/**
 * 합격자를 일괄 산출합니다.
 *
 * @property dryRun true이면 상태를 저장하지 않고 산출 결과만 돌려준다
 */
data class EvaluateScreeningCommand(
    val dryRun: Boolean = false,
)

/**
 * 내보내기 작업을 생성합니다.
 */
data class CreateExportCommand(
    val type: ExportType,
    val filter: ApplicantFilter = ApplicantFilter(),
)

/**
 * 공지사항을 등록합니다.
 */
data class CreateNoticeCommand(
    val title: String,
    val content: String,
    val isPinned: Boolean = false,
    val attachmentIds: List<String> = emptyList(),
)

/**
 * 지원자 질문에 답변합니다.
 */
data class AnswerQuestionCommand(
    val questionId: Long,
    val content: String,
    val answeredBy: String,
)
