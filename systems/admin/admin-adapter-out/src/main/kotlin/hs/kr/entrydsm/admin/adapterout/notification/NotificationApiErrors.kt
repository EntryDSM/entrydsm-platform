package hs.kr.entrydsm.admin.adapterout.notification

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.notification.api.NotificationApiException

/**
 * notification 모듈의 실패를 admin 오류 코드로 옮긴다. gRPC 상태 코드를 옮기던 자리를 대신한다.
 *
 * 예외를 삼키지 않고 다시 던진다. 합류한 트랜잭션이 롤백 전용으로 표시된 채 커밋되지 않게 한다.
 *
 * @param notFound 대상을 찾지 못했을 때 쓸 오류 코드. 없는 호출은 500 으로 둔다
 */
internal fun <T> callNotification(
    notFound: ErrorCode = ErrorCode.INTERNAL_SERVER_ERROR,
    block: () -> T,
): T =
    try {
        block()
    } catch (exception: NotificationApiException) {
        throw AdminDomainException(
            when (exception) {
                is NotificationApiException.InvalidArgument -> ErrorCode.INVALID_REQUEST_BODY
                is NotificationApiException.NotFound -> notFound
            },
            exception,
        )
    }
