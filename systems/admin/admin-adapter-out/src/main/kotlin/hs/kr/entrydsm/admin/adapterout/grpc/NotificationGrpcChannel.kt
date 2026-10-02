package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * notification 으로 나가는 gRPC 연결입니다.
 *
 * 공지 등록과 질문 답변이 같은 서비스를 부르므로 채널을 하나만 두고 나눠 씁니다.
 */
@Component
class NotificationGrpcChannel(
    @Value("\${notification.grpc.host}") host: String,
    @Value("\${notification.grpc.port}") port: Int,
    @Value("\${notification.grpc.deadline-ms}") val deadlineMs: Long,
) : DisposableBean {
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}

/**
 * gRPC 실패를 admin 오류 코드로 옮깁니다.
 *
 * @param notFound 대상을 찾지 못했을 때 쓸 오류 코드. 없는 호출은 500 으로 둔다
 * @param unavailable 상대 서비스가 응답하지 않을 때 쓸 오류 코드. 상대가 그 RPC 를 아직 모르는
 *   UNIMPLEMENTED 도 여기로 둔다 — 상대를 나중에 배포하는 동안 500 이 나가면 게이트웨이 서킷이 열린다
 */
internal fun StatusRuntimeException.toAdminException(
    notFound: ErrorCode = ErrorCode.INTERNAL_SERVER_ERROR,
    unavailable: ErrorCode = ErrorCode.NOTIFICATION_SERVICE_UNAVAILABLE,
    failedPrecondition: ErrorCode = ErrorCode.INTERNAL_SERVER_ERROR,
    invalidArgument: ErrorCode = ErrorCode.UPSTREAM_INVALID_ARGUMENT,
    rpc: String = "unknown",
    targetIds: List<Long> = emptyList(),
): AdminDomainException =
    AdminDomainException(
        when (status.code) {
            Status.Code.INVALID_ARGUMENT -> when (status.description) {
                "APPLICATION_INVALID_ID" -> ErrorCode.APPLICATION_INVALID_ID
                "APPLICATION_FORM_INVALID" -> ErrorCode.APPLICATION_FORM_INVALID
                "APPLICATION_SCORE_INVALID" -> ErrorCode.APPLICATION_SCORE_INVALID
                else -> invalidArgument
            }
            Status.Code.DATA_LOSS -> when (status.description) {
                "APPLICATION_SCORE_INVALID" -> ErrorCode.APPLICATION_SCORE_INVALID
                "APPLICATION_FORM_INVALID" -> ErrorCode.APPLICATION_FORM_INVALID
                else -> invalidArgument
            }
            Status.Code.NOT_FOUND -> notFound
            Status.Code.FAILED_PRECONDITION -> failedPrecondition
            Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED, Status.Code.UNIMPLEMENTED -> unavailable
            else -> ErrorCode.INTERNAL_SERVER_ERROR
        },
        this,
        rpc = rpc,
        grpcStatus = status.code.name,
        targetIds = targetIds,
    ).also {
        // 원격 설명은 임의의 개인정보를 포함할 수 있으므로 허용된 코드만 로그에 남긴다.
        org.slf4j.LoggerFactory.getLogger("admin.grpc").error(
            "Upstream gRPC failed [rpc={}, targetIds={}, status={}, code={}]", rpc, targetIds, status.code, it.errorCode,
        )
    }
