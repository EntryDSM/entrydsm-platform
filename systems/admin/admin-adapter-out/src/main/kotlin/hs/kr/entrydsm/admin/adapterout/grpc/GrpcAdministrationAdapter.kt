package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.command.*
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.*
import hs.kr.entrydsm.admin.domain.port.`in`.*
import hs.kr.entrydsm.application.grpc.AdministrationRequest
import hs.kr.entrydsm.application.grpc.AdministrationResponse
import hs.kr.entrydsm.application.grpc.AdministrationServiceGrpc
import io.grpc.StatusRuntimeException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.util.concurrent.TimeUnit

/** 관리자 BFF는 도메인 판단과 저장 없이 인증된 요청만 전달한다. */
@Component
class GrpcAdministrationAdapter(private val grpc: ApplicationGrpcChannel) :
    ReadApplicantUseCase, UpdateApplicantUseCase, IssueExamineeNumberUseCase,
    DeleteApplicantUseCase, UpdateScorePolicyUseCase, UpdateAdmissionQuotaUseCase,
    EvaluateFirstScreeningUseCase, EvaluateFinalScreeningUseCase, ReadStatisticsUseCase,
    CreateExportUseCase, ReadExportUseCase {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val stub = AdministrationServiceGrpc.newBlockingStub(grpc.channel)

    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> =
        call(SearchApplicantsQuery(filter, pageRequest), "searchApplicants", true) { searchApplicants(it) }

    override fun findDetail(applicantId: Long): ApplicantDetail =
        call(ApplicantIdQuery(applicantId), "readApplicant", false) { readApplicant(it) }

    override fun updateArrival(command: UpdateArrivalCommand) { call<Unit>(command, "updateArrival", false) { updateArrival(it) } }

    override fun updateStatus(command: UpdateApplicantStatusCommand) { call<Unit>(command, "updateApplicantStatus", false) { updateApplicantStatus(it) } }

    override fun issueAll(): ExamineeNumberIssueResult =
        call(Unit, "issueExamineeNumbers", true) { issueExamineeNumbers(it) }

    override fun delete(applicantId: Long) { call<Unit>(ApplicantIdQuery(applicantId), "deleteApplicant", false) { deleteApplicant(it) } }

    fun readPolicy(): ScorePolicy =
        call(Unit, "readScorePolicy", false) { readScorePolicy(it) }

    override fun update(command: UpdateScorePolicyCommand) { call<Unit>(command, "updateScorePolicy", false) { updateScorePolicy(it) } }

    fun readQuota(): AdmissionQuota =
        call(Unit, "readAdmissionQuota", false) { readAdmissionQuota(it) }

    override fun update(command: UpdateAdmissionQuotaCommand): AdmissionQuota =
        call(command, "updateAdmissionQuota", false) { updateAdmissionQuota(it) }

    override fun evaluateFirst(command: EvaluateScreeningCommand): ScreeningResult =
        call(command, "evaluateFirstScreening", true) { evaluateFirstScreening(it) }

    override fun evaluateFinal(applicantId: Long): FinalScreeningResult =
        call(ApplicantIdQuery(applicantId), "evaluateFinalScreening", false) { evaluateFinalScreening(it) }

    override fun collect(metrics: Set<StatisticsMetric>): ApplicantStatistics =
        call(StatisticsQuery(metrics), "collectStatistics", true) { collectStatistics(it) }

    override fun create(command: CreateExportCommand): ExportJob =
        call(command, "createExport", false) { createExport(it) }

    override fun findById(exportJobId: String): ExportJobView =
        call(ExportIdQuery(exportJobId), "readExport", false) { readExport(it) }

    private inline fun <reified T> call(command: Any, rpc: String, list: Boolean,
        action: AdministrationServiceGrpc.AdministrationServiceBlockingStub.(AdministrationRequest) -> AdministrationResponse): T {
        val http = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
            ?: throw AdminDomainException(ErrorCode.AUTH_UNAUTHORIZED)
        val userId = http.getHeader("X-User-Id")?.takeIf { it.isNotBlank() }
            ?: throw AdminDomainException(ErrorCode.AUTH_UNAUTHORIZED)
        val role = http.getHeader("X-User-Role")
        if (role != "ADMIN") throw AdminDomainException(ErrorCode.ACCESS_DENIED)
        val request = AdministrationRequest.newBuilder().setUserId(userId).setUserRole(role)
            .setCommandJson(if (command == Unit) "{}" else mapper.writeValueAsString(command)).build()
        val response = try {
            stub.withDeadlineAfter(if (list) grpc.listDeadlineMs else grpc.deadlineMs, TimeUnit.MILLISECONDS).action(request)
        } catch (exception: StatusRuntimeException) {
            throw exception.toAdminException(notFound = ErrorCode.APPLICANT_NOT_FOUND,
                unavailable = ErrorCode.APPLICATION_SERVICE_UNAVAILABLE,
                failedPrecondition = ErrorCode.INVALID_STATUS_TRANSITION, rpc = "AdministrationService/$rpc")
        }
        if (response.hasErrorCode()) {
            val code = ErrorCode.entries.firstOrNull { it.name == response.errorCode } ?: ErrorCode.INTERNAL_SERVER_ERROR
            throw AdminDomainException(code, failedCount = response.failedCount,
                totalCount = response.totalCount.takeIf { response.hasTotalCount() }, targetIds = response.targetIdsList,
                rpc = "AdministrationService/$rpc")
        }
        if (T::class == Unit::class) return Unit as T
        return try { mapper.readValue(response.dataJson, object : TypeReference<T>() {}) }
        catch (exception: tools.jackson.core.JacksonException) {
            throw AdminDomainException(ErrorCode.APPLICATION_FORM_INVALID, exception, rpc = "AdministrationService/$rpc")
        }
    }
}

@Configuration(proxyBeanMethods = false)
class AdministrationReadUseCaseConfig {
    @Bean
    fun readScorePolicy(adapter: GrpcAdministrationAdapter): ReadScorePolicyUseCase =
        object : ReadScorePolicyUseCase { override fun findCurrent() = adapter.readPolicy() }

    @Bean
    fun readAdmissionQuota(adapter: GrpcAdministrationAdapter): ReadAdmissionQuotaUseCase =
        object : ReadAdmissionQuotaUseCase { override fun findCurrent() = adapter.readQuota() }
}
