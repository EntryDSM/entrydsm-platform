package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPdfs
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import hs.kr.entrydsm.configuration.grpc.ConfigurationServiceGrpc
import hs.kr.entrydsm.configuration.grpc.RenderApplicationEssayRequest
import io.grpc.StatusRuntimeException
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class GrpcApplicationEssayAdapter(private val grpc: ConfigurationGrpcChannel) : ApplicationEssayPort {
    private val stub = ConfigurationServiceGrpc.newBlockingStub(grpc.channel)

    override fun render(applicantId: Long, examineeNumber: String): ApplicationEssayPdfs = try {
        stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS)
            .renderApplicationEssay(
                RenderApplicationEssayRequest.newBuilder()
                    .setApplicantId(applicantId)
                    .setExamineeNumber(examineeNumber)
                    .build(),
            )
            .let {
                ApplicationEssayPdfs(
                    it.introductionPdf.toByteArray().takeIf { _ -> it.hasIntroductionPdf() },
                    it.studyPlanPdf.toByteArray().takeIf { _ -> it.hasStudyPlanPdf() },
                )
            }
    } catch (exception: StatusRuntimeException) {
        throw exception.toAdminException(
            notFound = ErrorCode.APPLICANT_NOT_FOUND,
            unavailable = ErrorCode.ESSAY_GENERATION_FAILED,
        )
    }

    override fun renderBatch(targets: List<Pair<Long, String>>): List<ApplicationEssayPdfs> {
        if (targets.isEmpty()) return emptyList()
        val executor = Executors.newFixedThreadPool(minOf(targets.size, MAX_PARALLEL_REQUESTS))
        return try {
            executor.invokeAll(targets.map { (id, number) -> Callable { render(id, number) } })
                .map { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }

    private companion object {
        const val MAX_PARALLEL_REQUESTS = 4
    }
}
