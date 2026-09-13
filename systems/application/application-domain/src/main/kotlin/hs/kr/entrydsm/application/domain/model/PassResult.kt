package hs.kr.entrydsm.application.domain.model

import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.ResultType
import java.time.LocalDateTime

/**
 * 지원자 한 명의 전형 단계별 발표 결과입니다.
 *
 * 산출은 admin 이 하지만 발표 결과의 소유자는 application 입니다. 수험생에게 보이는
 * 합격 여부는 이 값에서만 나옵니다.
 *
 * @property processedBy 결과를 반영한 관리자 계정. 자동 산출이면 null
 */
data class PassResult(
    val applicantId: Long,
    val resultType: ResultType,
    val result: PassResultStatus,
    val processedBy: Long? = null,
    val processedAt: LocalDateTime,
) {
    init {
        require(applicantId > 0) { "applicantId must be positive" }
        require(result != PassResultStatus.PENDING) {
            "PENDING is not an announcement; omit the entry instead"
        }
    }
}
