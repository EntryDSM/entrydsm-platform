package hs.kr.entrydsm.admin.domain.exception

import hs.kr.entrydsm.admin.domain.enum.ErrorCode

class AdminDomainException(
    errorCode: ErrorCode,
    cause: Throwable? = null,
    val failedCount: Int = 0,
    val totalCount: Int? = null,
    val rpc: String? = null,
    val grpcStatus: String? = null,
    val targetIds: List<Long> = emptyList(),
) : AdminException(errorCode, cause)
