package hs.kr.entrydsm.application.adapterout.grpc

import hs.kr.entrydsm.application.application.exception.ApplicationPeriodLookupFailedException
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.GetScheduleRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 관리자 화면과 프론트가 원서 접수 일정을 이 제목으로 찾는다. */
private const val APPLICATION_SCHEDULE_TITLE = "원서 접수"

/**
 * 관리자가 configuration 에 등록한 원서 접수 일정을 읽습니다.
 *
 * ponytail: 원서를 쓰는 요청마다 부른다. 관리자가 일정을 고치면 바로 따르는 대신, configuration 이
 * 느려지면 요청도 deadline 만큼 늦어진다. 문제가 되면 짧은 캐시를 둔다.
 */
@Component
class GrpcApplicationPeriodAdapter(
    @Value("\${configuration.grpc.host}") host: String,
    @Value("\${configuration.grpc.port}") port: Int,
    @Value("\${configuration.grpc.deadline-ms}") private val deadlineMs: Long,
) : ApplicationPeriodReader, DisposableBean {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()
    private val stub = ConfigurationServiceGrpc.newBlockingStub(channel)

    override fun read(): ClosedRange<Instant>? =
        try {
            val schedule = stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .getSchedule(GetScheduleRequest.newBuilder().setTitle(APPLICATION_SCHEDULE_TITLE).build())
            Instant.ofEpochMilli(schedule.startAtEpochMillis)..Instant.ofEpochMilli(schedule.endAtEpochMillis)
        } catch (exception: StatusRuntimeException) {
            // 일정이 없으면 기간이 아니다. 장애나 새 RPC 가 없는 옛 configuration 은 확인하지 못한 것이다.
            if (exception.status.code == Status.Code.NOT_FOUND) null else throw ApplicationPeriodLookupFailedException(exception)
        }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
