package hs.kr.entrydsm.application.administration.adapterin.grpc

import hs.kr.entrydsm.admin.domain.command.*
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.`in`.*
import hs.kr.entrydsm.application.grpc.AdministrationRequest
import hs.kr.entrydsm.application.grpc.AdministrationResponse
import hs.kr.entrydsm.application.grpc.AdministrationServiceGrpc
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class AdministrationGrpcService(
    private val applicants: ReadApplicantUseCase,
    private val updates: UpdateApplicantUseCase,
    private val numbers: IssueExamineeNumberUseCase,
    private val deletion: DeleteApplicantUseCase,
    private val policies: ReadScorePolicyUseCase,
    private val policyUpdates: UpdateScorePolicyUseCase,
    private val quotas: ReadAdmissionQuotaUseCase,
    private val quotaUpdates: UpdateAdmissionQuotaUseCase,
    private val first: EvaluateFirstScreeningUseCase,
    private val final: EvaluateFinalScreeningUseCase,
    private val statistics: ReadStatisticsUseCase,
    private val exports: CreateExportUseCase,
    private val exportQueries: ReadExportUseCase,
) : AdministrationServiceGrpc.AdministrationServiceImplBase() {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    override fun searchApplicants(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { val query = request.read<SearchApplicantsQuery>(); applicants.search(query.filter, query.page) }

    override fun readApplicant(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { applicants.findDetail(request.applicantId()) }

    override fun updateArrival(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { val command = request.read<UpdateArrivalCommand>(); require(command.applicantId > 0); updates.updateArrival(command) }

    override fun updateApplicantStatus(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { val command = request.read<UpdateApplicantStatusCommand>(); require(command.applicantId > 0); updates.updateStatus(command) }

    override fun issueExamineeNumbers(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { numbers.issueAll() }

    override fun deleteApplicant(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { deletion.delete(request.applicantId()) }

    override fun readScorePolicy(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { policies.findCurrent() }

    override fun updateScorePolicy(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { policyUpdates.update(request.read<UpdateScorePolicyCommand>().copy(updatedBy = request.userId)) }

    override fun readAdmissionQuota(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { quotas.findCurrent() }

    override fun updateAdmissionQuota(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { quotaUpdates.update(request.read<UpdateAdmissionQuotaCommand>().copy(updatedBy = request.userId)) }

    override fun evaluateFirstScreening(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { first.evaluateFirst(request.read<EvaluateScreeningCommand>()) }

    override fun evaluateFinalScreening(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { final.evaluateFinal(request.applicantId()) }

    override fun collectStatistics(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { statistics.collect(request.read<StatisticsQuery>().metrics) }

    override fun createExport(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { exports.create(request.read<CreateExportCommand>()) }

    override fun readExport(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>) =
        respond(request, observer) { val id = request.read<ExportIdQuery>().exportJobId; require(id.isNotBlank()); exportQueries.findById(id) }

    private inline fun <reified T> AdministrationRequest.read(): T =
        mapper.readValue(commandJson, T::class.java)

    private fun AdministrationRequest.applicantId(): Long =
        read<ApplicantIdQuery>().applicantId.also { require(it > 0) }

    private fun respond(request: AdministrationRequest, observer: StreamObserver<AdministrationResponse>, action: () -> Any?) {
        val response = try {
            if (request.userId.isBlank()) throw AdminDomainException(ErrorCode.AUTH_UNAUTHORIZED)
            if (request.userRole != "ADMIN") throw AdminDomainException(ErrorCode.ACCESS_DENIED)
            require(request.commandJson.length <= 1024 * 1024)
            val result = action()
            AdministrationResponse.newBuilder()
                .setDataJson(if (result == Unit) "null" else mapper.writeValueAsString(result)).build()
        } catch (exception: AdminDomainException) {
            AdministrationResponse.newBuilder().setErrorCode(exception.errorCode.name)
                .setFailedCount(exception.failedCount).addAllTargetIds(exception.targetIds)
                .also { builder -> exception.totalCount?.let(builder::setTotalCount) }.build()
        } catch (exception: IllegalArgumentException) {
            AdministrationResponse.newBuilder().setErrorCode(ErrorCode.INVALID_REQUEST_BODY.name).build()
        } catch (exception: tools.jackson.core.JacksonException) {
            AdministrationResponse.newBuilder().setErrorCode(ErrorCode.INVALID_REQUEST_BODY.name).build()
        } catch (exception: Exception) {
            // 요청 본문·원서 데이터·예외 메시지는 응답에 넣지 않는다.
            org.slf4j.LoggerFactory.getLogger(javaClass).error("Administration operation failed [exception={}]", exception.javaClass.name)
            AdministrationResponse.newBuilder().setErrorCode(ErrorCode.INTERNAL_SERVER_ERROR.name).build()
        }
        observer.onNext(response)
        observer.onCompleted()
    }
}
