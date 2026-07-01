package hs.kr.entrydsm.configuration.adapterin.grpc

import hs.kr.entrydsm.configuration.domain.command.CreateEnvironmentVariableCommand
import hs.kr.entrydsm.configuration.domain.command.UpdateEnvironmentVariableCommand
import hs.kr.entrydsm.configuration.domain.port.`in`.CreateEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.DeleteEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.ReadEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.domain.port.`in`.UpdateEnvironmentVariableUseCase
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.CreateEnvironmentVariableRequest
import hs.kr.entrydsm.configuration.grpc.DeleteEnvironmentVariableRequest
import hs.kr.entrydsm.configuration.grpc.DeleteEnvironmentVariableResponse
import hs.kr.entrydsm.configuration.grpc.EnvironmentVariableResponse
import hs.kr.entrydsm.configuration.grpc.GetAllEnvironmentVariablesRequest
import hs.kr.entrydsm.configuration.grpc.GetAllEnvironmentVariablesResponse
import hs.kr.entrydsm.configuration.grpc.GetEnvironmentVariableRequest
import hs.kr.entrydsm.configuration.grpc.UpdateEnvironmentVariableRequest
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component

@Component
class ConfigurationGrpcService(
    private val createUseCase: CreateEnvironmentVariableUseCase,
    private val readUseCase: ReadEnvironmentVariableUseCase,
    private val updateUseCase: UpdateEnvironmentVariableUseCase,
    private val deleteUseCase: DeleteEnvironmentVariableUseCase,
) : ConfigurationServiceGrpc.ConfigurationServiceImplBase() {

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
