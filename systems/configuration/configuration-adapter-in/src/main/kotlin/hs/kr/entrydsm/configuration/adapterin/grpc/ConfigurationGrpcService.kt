package hs.kr.entrydsm.configuration.adapterin.grpc

import com.google.protobuf.ByteString
import hs.kr.entrydsm.configuration.domain.command.CreateEnvironmentVariableCommand
import hs.kr.entrydsm.configuration.domain.command.UpdateEnvironmentVariableCommand
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantLookupFailedException
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.CreateEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.DeleteEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.ReadEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.UpdateEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.schedule.port.`in`.ScheduleUseCase
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.CreateEnvironmentVariableRequest
import hs.kr.entrydsm.configuration.grpc.DeleteEnvironmentVariableRequest
import hs.kr.entrydsm.configuration.grpc.DeleteEnvironmentVariableResponse
import hs.kr.entrydsm.configuration.grpc.EnvironmentVariableResponse
import hs.kr.entrydsm.configuration.grpc.GetAllEnvironmentVariablesRequest
import hs.kr.entrydsm.configuration.grpc.GetAllEnvironmentVariablesResponse
import hs.kr.entrydsm.configuration.grpc.GetEnvironmentVariableRequest
import hs.kr.entrydsm.configuration.grpc.GetScheduleRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsRequest
import hs.kr.entrydsm.configuration.grpc.RenderAdmissionTicketsResponse
import hs.kr.entrydsm.configuration.grpc.ScheduleResponse
import hs.kr.entrydsm.configuration.grpc.UpdateEnvironmentVariableRequest
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.stereotype.Component

/** 관리자는 일정을 한국 시각으로 넣는다. */
private val SEOUL = ZoneId.of("Asia/Seoul")

@Component
class ConfigurationGrpcService(
    private val createUseCase: CreateEnvironmentVariableUseCase,
    private val readUseCase: ReadEnvironmentVariableUseCase,
    private val updateUseCase: UpdateEnvironmentVariableUseCase,
    private val deleteUseCase: DeleteEnvironmentVariableUseCase,
    private val applicantFileUseCase: ApplicantFileUseCase,
    private val scheduleUseCase: ScheduleUseCase,
) : ConfigurationServiceGrpc.ConfigurationServiceImplBase() {

    /** admin 수험표 일괄 출력이 한 번 부른다. 한 명이라도 실패하면 전체가 실패하고, admin 은 이를 작업 실패로만 쓴다. */
    override fun renderAdmissionTickets(
        request: RenderAdmissionTicketsRequest,
        responseObserver: StreamObserver<RenderAdmissionTicketsResponse>,
    ) {
        val xlsx = try {
            applicantFileUseCase.renderAdmissionTickets(
                request.ticketsList.map { target ->
                    target.applicantId to target.examineeNumber.takeIf { target.hasExamineeNumber() }
                },
            )
        } catch (exception: Exception) {
            return responseObserver.onError(
                when (exception) {
                    is ApplicantNotFoundException -> Status.NOT_FOUND
                    is ApplicantLookupFailedException -> Status.UNAVAILABLE
                    else -> Status.INTERNAL
                }.withDescription(exception.message).withCause(exception).asRuntimeException(),
            )
        }
        responseObserver.onNext(RenderAdmissionTicketsResponse.newBuilder().setXlsx(ByteString.copyFrom(xlsx)).build())
        responseObserver.onCompleted()
    }

    /** application 이 원서 접수 기간을 확인할 때 부른다. */
    override fun getSchedule(
        request: GetScheduleRequest,
        responseObserver: StreamObserver<ScheduleResponse>,
    ) {
        val schedule = scheduleUseCase.findByTitle(request.title)
            ?: return responseObserver.onError(
                Status.NOT_FOUND.withDescription("schedule not found: ${request.title}").asRuntimeException(),
            )
        responseObserver.onNext(
            ScheduleResponse.newBuilder()
                .setTitle(schedule.title)
                .setStartAtEpochMillis(schedule.startAt.toEpochMilli())
                .setEndAtEpochMillis(schedule.endAt.toEpochMilli())
                .build(),
        )
        responseObserver.onCompleted()
    }

    override fun createEnvironmentVariable(
        request: CreateEnvironmentVariableRequest,
        responseObserver: StreamObserver<EnvironmentVariableResponse>,
    ) {
        val result = createUseCase.create(
            CreateEnvironmentVariableCommand(
                key = request.key,
                value = request.value,
                description = if (request.hasDescription()) request.description else null,
            )
        )
        responseObserver.onNext(result.toResponse())
        responseObserver.onCompleted()
    }

    override fun getEnvironmentVariable(
        request: GetEnvironmentVariableRequest,
        responseObserver: StreamObserver<EnvironmentVariableResponse>,
    ) {
        val result = readUseCase.findByKey(request.key)
        responseObserver.onNext(result.toResponse())
        responseObserver.onCompleted()
    }

    override fun getAllEnvironmentVariables(
        request: GetAllEnvironmentVariablesRequest,
        responseObserver: StreamObserver<GetAllEnvironmentVariablesResponse>,
    ) {
        val items = readUseCase.findAll().map { it.toResponse() }
        responseObserver.onNext(
            GetAllEnvironmentVariablesResponse.newBuilder()
                .addAllItems(items)
                .build()
        )
        responseObserver.onCompleted()
    }

    override fun updateEnvironmentVariable(
        request: UpdateEnvironmentVariableRequest,
        responseObserver: StreamObserver<EnvironmentVariableResponse>,
    ) {
        val result = updateUseCase.update(
            UpdateEnvironmentVariableCommand(
                key = request.key,
                value = request.value,
                description = if (request.hasDescription()) request.description else null,
            )
        )
        responseObserver.onNext(result.toResponse())
        responseObserver.onCompleted()
    }

    override fun deleteEnvironmentVariable(
        request: DeleteEnvironmentVariableRequest,
        responseObserver: StreamObserver<DeleteEnvironmentVariableResponse>,
    ) {
        deleteUseCase.delete(request.key)
        responseObserver.onNext(
            DeleteEnvironmentVariableResponse.newBuilder()
                .setSuccess(true)
                .build()
        )
        responseObserver.onCompleted()
    }

    private fun hs.kr.entrydsm.configuration.domain.EnvironmentVariable.toResponse(): EnvironmentVariableResponse {
        val builder = EnvironmentVariableResponse.newBuilder()
            .setId(id ?: 0L)
            .setKey(key)
            .setValue(value)
        description?.let { builder.setDescription(it) }
        return builder.build()
    }
}

private fun LocalDateTime.toEpochMilli(): Long = atZone(SEOUL).toInstant().toEpochMilli()
