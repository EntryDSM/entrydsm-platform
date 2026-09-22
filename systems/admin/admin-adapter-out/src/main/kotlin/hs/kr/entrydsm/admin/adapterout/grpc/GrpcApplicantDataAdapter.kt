package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantArrivalPort
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.UpdateApplicantArrivalRequest
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 지원자를 application gRPC 로 읽고 admin 의 전형 정보를 덧붙입니다.
 *
 * 원서 내용은 application 이 갖고 admin 은 `screening` 행만 가지므로, 두 곳을 합쳐야
 * 지원자 한 명이 됩니다. 전형 정보가 없는 지원자는 미도착·수험 번호 없음·`PENDING` 입니다.
 *
 * ponytail: 목록 필터·정렬·페이징을 메모리에서 한다. 한 회차 수천 명 규모라 충분하다
 * (통계 집계도 같은 이유로 전체를 읽는다). 키워드가 이름(application)과 수험 번호(admin)를
 * 함께 보는 탓에 어차피 합친 뒤에만 걸 수 있다. 만 단위로 커지면 application 에 필터·페이징
 * RPC 를 더해 그쪽으로 넘긴다.
 */
@Component
class GrpcApplicantDataAdapter(
    private val grpc: ApplicationGrpcChannel,
    private val screeningJpaRepository: ScreeningJpaRepository,
) : ApplicantRepository, ApplicantArrivalPort {
    private val stub = ApplicationServiceGrpc.newBlockingStub(grpc.channel)

    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> {
        val matched = findAll(filter)

        return Page(
            items = matched
                .drop((pageRequest.normalizedPage - 1) * pageRequest.normalizedSize)
                .take(pageRequest.normalizedSize),
            page = pageRequest.normalizedPage,
            size = pageRequest.normalizedSize,
            totalElements = matched.size.toLong(),
        )
    }

    override fun findAll(filter: ApplicantFilter): List<Applicant> {
        val screenings = screeningJpaRepository.findAll().associateBy { it.applicantId }

        return call { listStub().listApplicants(ListApplicantsRequest.getDefaultInstance()) }
            .applicantsList
            .map { it.toApplicant(screenings[it.applicantId]) }
            .filter { it.matches(filter) }
            .sortedBy { it.id }
    }

    override fun findById(applicantId: Long): Applicant? =
        getApplicant(applicantId)?.toApplicant(screeningJpaRepository.findById(applicantId).orElse(null))

    /**
     * 자기소개서·학업계획서는 원서 주인 계정으로 찾는 원서 내용(`GetApplicationForm`)에만 있다.
     * `ApplicantResponse` 에 얹으면 `ListApplicants` 가 제출 원서 전체의 본문을 한 메시지로 나르게 된다.
     * document 가 관리자 원서 출력에 쓰는 순서(지원자 → 주인 계정 → 원서 내용)와 같다.
     */
    override fun findDetailById(applicantId: Long): ApplicantDetail? {
        val response = getApplicant(applicantId) ?: return null
        val form = call {
            oneStub().getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(response.userId).build())
        }

        return ApplicantDetail(
            applicant = response.toApplicant(screeningJpaRepository.findById(applicantId).orElse(null)),
            photoFileId = form.photoFileId.takeIf { form.hasPhotoFileId() },
            introduction = form.introduction.takeIf { form.hasIntroduction() },
            studyPlan = form.studyPlan.takeIf { form.hasStudyPlan() },
        )
    }

    private fun getApplicant(applicantId: Long): ApplicantResponse? =
        try {
            oneStub().getApplicant(GetApplicantRequest.newBuilder().setApplicantId(applicantId).build())
        } catch (exception: StatusRuntimeException) {
            // 0 이하 id 는 application 이 INVALID_ARGUMENT 로 거절한다. 없는 지원자와 같다.
            if (exception.status.code in NO_APPLICANT) null else throw exception.toApplicationException()
        }

    override fun save(applicant: Applicant): Applicant {
        screeningJpaRepository.save(applicant.toScreening())
        return applicant
    }

    override fun saveAll(applicants: List<Applicant>): List<Applicant> {
        screeningJpaRepository.saveAll(applicants.map { it.toScreening() })
        return applicants
    }

    override fun update(applicantId: Long, isArrived: Boolean) {
        call {
            oneStub().updateApplicantArrival(
                UpdateApplicantArrivalRequest.newBuilder()
                    .setApplicantId(applicantId)
                    .setIsArrived(isArrived)
                    .build(),
            )
        }
    }

    private fun ApplicantResponse.toApplicant(screening: ScreeningJpaEntity?) = Applicant(
        id = applicantId,
        name = name.takeIf { hasName() },
        // application 이 ISO-8601 로 넣는다. 어긋난 값 하나가 목록 전체를 막지 않게 빈 값으로 둔다.
        birthDate = birthdate.takeIf { hasBirthdate() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        phoneNumber = phoneNumber.takeIf { hasPhoneNumber() },
        region = when (region) {
            GrpcRegion.REGION_DAEJEON -> Region.DAEJEON
            GrpcRegion.REGION_NATIONAL -> Region.NATIONWIDE
            else -> null
        },
        admissionType = when (admissionType) {
            GrpcAdmissionType.ADMISSION_TYPE_REGULAR -> AdmissionType.GENERAL
            GrpcAdmissionType.ADMISSION_TYPE_MEISTER -> AdmissionType.MEISTER
            GrpcAdmissionType.ADMISSION_TYPE_SOCIAL -> AdmissionType.SOCIAL
            else -> null
        },
        graduationStatus = when (graduationType) {
            GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE -> GraduationStatus.EXPECTED
            GrpcGraduationType.GRADUATION_TYPE_GRADUATED -> GraduationStatus.GRADUATED
            GrpcGraduationType.GRADUATION_TYPE_GED -> GraduationStatus.GED
            else -> null
        },
        schoolName = schoolName.takeIf { hasSchoolName() },
        totalScore = totalScore.takeIf { hasTotalScore() },
        submittedAt = submittedAtEpochMillis.takeIf { hasSubmittedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        examineeNumber = screening?.examineeNumber,
        isArrived = screening?.isArrived ?: false,
        status = screening?.status ?: ApplicantStatus.PENDING,
        arrivedAt = screening?.arrivedAt,
        updatedAt = screening?.updatedAt,
    )

    private fun Applicant.toScreening() = ScreeningJpaEntity(
        applicantId = id,
        examineeNumber = examineeNumber,
        isArrived = isArrived,
        status = status,
        arrivedAt = arrivedAt,
        updatedAt = updatedAt,
    )

    private fun Applicant.matches(filter: ApplicantFilter): Boolean {
        val keyword = filter.keyword?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        return (
            keyword == null ||
                name?.lowercase()?.contains(keyword) == true ||
                examineeNumber?.lowercase()?.contains(keyword) == true
            ) &&
            (filter.regions.isEmpty() || region in filter.regions) &&
            (filter.admissionTypes.isEmpty() || admissionType in filter.admissionTypes) &&
            (filter.graduationStatuses.isEmpty() || graduationStatus in filter.graduationStatuses) &&
            (filter.isArrived == null || isArrived == filter.isArrived) &&
            (filter.statuses.isEmpty() || status in filter.statuses)
    }

    private fun oneStub() = stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS)

    private fun listStub() = stub.withDeadlineAfter(grpc.listDeadlineMs, TimeUnit.MILLISECONDS)

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (exception: StatusRuntimeException) {
            throw exception.toApplicationException()
        }

    private fun StatusRuntimeException.toApplicationException() =
        toAdminException(
            notFound = ErrorCode.APPLICANT_NOT_FOUND,
            unavailable = ErrorCode.APPLICATION_SERVICE_UNAVAILABLE,
            failedPrecondition = ErrorCode.INVALID_STATUS_TRANSITION,
        )

    private companion object {
        val NO_APPLICANT = setOf(Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT)
    }
}
