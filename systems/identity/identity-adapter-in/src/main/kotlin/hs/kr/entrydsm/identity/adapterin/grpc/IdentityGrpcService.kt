package hs.kr.entrydsm.identity.adapterin.grpc

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.grpc.IdentityServiceGrpc
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneRequest
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component

@Component
class IdentityGrpcService(private val accounts: AccountPort) : IdentityServiceGrpc.IdentityServiceImplBase() {
    override fun validateApplicationPhone(request: ValidateApplicationPhoneRequest, observer: StreamObserver<ValidateApplicationPhoneResponse>) {
        val response = try {
            require(request.accountId > 0 && request.phoneNumber.matches(Regex("010[0-9]{8}")))
            ValidateApplicationPhoneResponse.newBuilder()
                .setValid(accounts.validateApplicationPhone(ReadAccountCommand(request.accountId), request.phoneNumber))
                .build()
        } catch (_: IllegalArgumentException) {
            return observer.onError(Status.INVALID_ARGUMENT.asRuntimeException())
        } catch (_: IdentityDomainException) {
            return observer.onError(Status.NOT_FOUND.asRuntimeException())
        } catch (_: Exception) {
            return observer.onError(Status.INTERNAL.asRuntimeException())
        }
        observer.onNext(response)
        observer.onCompleted()
    }
}
