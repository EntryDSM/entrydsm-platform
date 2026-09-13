package hs.kr.entrydsm.observability.adapterout.log

import hs.kr.entrydsm.observability.application.port.out.ServerLogPage
import hs.kr.entrydsm.observability.application.port.out.ServerLogStorePort
import hs.kr.entrydsm.observability.application.port.out.StatusFilter
import hs.kr.entrydsm.observability.domain.enum.ServiceName
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant
import org.springframework.stereotype.Component

/**
 * 서버 API 오류를 이 서비스로 보고하는 수집 경로가 문서에 정의되어 있지 않아 항상 빈 결과를 반환한다.
 * 각 서비스에 오류 리포팅 연동(예: gRPC/HTTP 인터셉터)이 추가되면 Redis 기반 어댑터로 교체한다.
 */
@Component
class EmptyServerLogStoreAdapter : ServerLogStorePort {
    override fun list(
        from: Instant,
        to: Instant,
        service: ServiceName?,
        status: StatusFilter?,
        cursor: Cursor?,
        size: Int,
    ): ServerLogPage = ServerLogPage(totalCount = 0, items = emptyList(), nextCursor = null, hasNext = false)
}
