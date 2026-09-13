package hs.kr.entrydsm.observability.application.port.out

fun interface LiveLogPublisherPort {
    /** 모든 인스턴스의 SSE 구독자에게 log 이벤트로 내보낸다. 구독자가 없으면 아무 일도 하지 않는다. */
    fun publishClientLog(input: ClientLogInput)
}
