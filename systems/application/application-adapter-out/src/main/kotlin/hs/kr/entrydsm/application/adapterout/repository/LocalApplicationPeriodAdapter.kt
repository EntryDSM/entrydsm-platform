package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import hs.kr.entrydsm.application.application.exception.ApplicationPeriodLookupFailedException
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 접수 기간은 원서와 같은 DB에서 읽는다. 일정 부재와 DB 장애를 구분한다. */
@Component
class LocalApplicationPeriodAdapter(private val schedules: ScheduleRepository) : ApplicationPeriodReader {
    @Transactional(readOnly = true)
    override fun readResultAnnouncedAt(): LocalDateTime? = try {
        schedules.findByTitle("1차 발표")?.startAt
    } catch (error: Exception) {
        throw ApplicationPeriodLookupFailedException(error)
    }

    @Transactional(readOnly = true)
    override fun read(): ClosedRange<Instant>? = try {
        schedules.findByTitle("원서 접수")?.let {
            val seoul = ZoneId.of("Asia/Seoul")
            it.startAt.atZone(seoul).toInstant()..it.endAt.atZone(seoul).toInstant()
        }
    } catch (error: Exception) {
        throw ApplicationPeriodLookupFailedException(error)
    }
}
