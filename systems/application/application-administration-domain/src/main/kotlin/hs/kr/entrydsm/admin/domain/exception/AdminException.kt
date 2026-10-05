package hs.kr.entrydsm.admin.domain.exception

import hs.kr.entrydsm.admin.domain.enum.ErrorCode

/**
 * Admin 도메인에서 의도적으로 발생시키는 예외의 기본 타입입니다.
 */
abstract class AdminException(
    val errorCode: ErrorCode,
    cause: Throwable? = null,
) : RuntimeException(errorCode.message, cause)
