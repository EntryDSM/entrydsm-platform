package hs.kr.entrydsm.observability.domain.exception

import hs.kr.entrydsm.observability.domain.enum.ErrorCode

/**
 * Monitor 도메인에서 의도적으로 발생시키는 예외의 기본 타입입니다.
 */
abstract class MonitorException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
