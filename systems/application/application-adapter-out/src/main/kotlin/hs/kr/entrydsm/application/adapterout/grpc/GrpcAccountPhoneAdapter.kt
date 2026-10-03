package hs.kr.entrydsm.application.adapterout.grpc

import hs.kr.entrydsm.application.application.exception.AccountPhoneLookupFailedException
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.identity.grpc.IdentityServiceGrpc
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GrpcAccountPhoneAdapter(
    @Value("\${identity.grpc.host}") host: String,
    @Value("\${identity.grpc.port}") port: Int,
    @Value("\${identity.grpc.deadline-ms}") private val deadlineMs: Long,
) : AccountPhoneValidator, DisposableBean {
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    private val stub = IdentityServiceGrpc.newBlockingStub(channel)

    override fun validate(accountId: Long, phoneNumber: String): Boolean = try {
        stub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS).validateApplicationPhone(
            ValidateApplicationPhoneRequest.newBuilder().setAccountId(accountId)
                .setPhoneNumber(phoneNumber.replace("-", "")).build(),
        ).valid
    } catch (failure: StatusRuntimeException) {
        throw AccountPhoneLookupFailedException(failure)
    }

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
