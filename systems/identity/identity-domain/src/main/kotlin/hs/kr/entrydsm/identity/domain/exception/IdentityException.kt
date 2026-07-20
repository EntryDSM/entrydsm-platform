package hs.kr.entrydsm.identity.domain.exception

import hs.kr.entrydsm.identity.domain.enum.ErrorCode

/**
 * Identity 도메인에서 의도적으로 발생시키는 예외의 기본 타입입니다.
 */
abstract class IdentityException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
