package hs.kr.entrydsm.application.application.port.out

import java.time.Instant

/** 관리자가 configuration 에 등록한 원서 접수 일정을 읽는다. */
fun interface ApplicationPeriodReader {
    /** 원서 접수 기간. 일정이 등록되지 않았으면 null. */
    fun read(): ClosedRange<Instant>?
}
