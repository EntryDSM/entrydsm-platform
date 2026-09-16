package hs.kr.entrydsm.application.api.event

import hs.kr.entrydsm.application.api.ApplicantStatus
import hs.kr.entrydsm.application.api.PassStatus
import java.time.Instant

/**
 * 원서 상태가 바뀌었다는 알림이다. `application.proto`의 `ApplicantStatusChangedEvent`를 대신한다.
 *
 * 이벤트는 바뀐 뒤의 상태 전체를 싣는다. [version] 은 원서마다 단조 증가하므로
 * 받는 쪽은 이미 반영한 버전 이하를 버리면 된다.
 */
data class ApplicantStatusChangedEvent(
    val eventId: String,
    val accountId: Long,
    val applicantStatus: ApplicantStatus,
    val passStatus: PassStatus,
    val version: Long,
    val submittedAt: Instant?,
    val announcedAt: Instant?,
    val occurredAt: Instant,
)

/**
 * 원서 상태 변경을 받을 모듈이 구현한다. application 모듈은 이 인터페이스만 알고 받는 쪽 모듈을 모른다.
 *
 * - 원서 트랜잭션이 커밋된 뒤, 저장해 둔 이벤트를 전달자가 이벤트마다 새 트랜잭션을 열어 호출한다.
 *   구현은 그 트랜잭션에 합류하므로 반영과 "전달 완료" 표시가 함께 커밋되거나 함께 롤백된다.
 * - 예외를 던지면 전달하지 않은 것으로 남고 다음 주기에 다시 호출된다. 같은 이벤트가 두 번 이상 올 수 있으므로
 *   [ApplicantStatusChangedEvent.version] 이나 [ApplicantStatusChangedEvent.eventId] 로 멱등하게 처리한다.
 */
fun interface ApplicantStatusChangedListener {
    fun onApplicantStatusChanged(event: ApplicantStatusChangedEvent)
}
