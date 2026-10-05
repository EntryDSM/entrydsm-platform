package hs.kr.entrydsm.application.grpcmapping

import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.domain.enum.*
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import java.time.ZoneOffset

fun ApplicantResult.toGrpcApplicant(): ApplicantResponse =
        ApplicantResponse.newBuilder()
            .setApplicantId(applicantId)
            .setUserId(accountId)
            .setRegion(region.toGrpc())
            .setAdmissionType(admissionType.toGrpc())
            .setGraduationType(graduationType.toGrpc())
            .setApplicantStatus(status.toGrpc())
            .setGender(
                when (gender) {
                    Gender.MALE -> GrpcGender.GENDER_MALE
                    Gender.FEMALE -> GrpcGender.GENDER_FEMALE
                    null -> GrpcGender.GENDER_UNSPECIFIED
                },
            )
            // apply 안에서는 name 이 빌더의 getName() 으로 잡히므로 also 로 넘긴다.
            .also { builder ->
                totalScore?.let(builder::setTotalScore)
                name?.let(builder::setName)
                schoolName?.let(builder::setSchoolName)
                photoFileId?.let(builder::setPhotoFileId)
                birthdate?.let { builder.setBirthdate(it.toString()) }
                phoneNumber?.let(builder::setPhoneNumber)
                submittedAt?.let {
                    builder.setSubmittedAtEpochMillis(it.toInstant(ZoneOffset.UTC).toEpochMilli())
                }
                address?.let(builder::setAddress)
            }
            .build()

    private fun Region?.toGrpc(): GrpcRegion = when (this) {
        Region.DAEJEON -> GrpcRegion.REGION_DAEJEON
        Region.NATIONAL -> GrpcRegion.REGION_NATIONAL
        null -> GrpcRegion.REGION_UNSPECIFIED
    }

    private fun AdmissionType?.toGrpc(): GrpcAdmissionType = when (this) {
        AdmissionType.REGULAR -> GrpcAdmissionType.ADMISSION_TYPE_REGULAR
        AdmissionType.MEISTER -> GrpcAdmissionType.ADMISSION_TYPE_MEISTER
        AdmissionType.SOCIAL -> GrpcAdmissionType.ADMISSION_TYPE_SOCIAL
        null -> GrpcAdmissionType.ADMISSION_TYPE_UNSPECIFIED
    }

    private fun GraduationType?.toGrpc(): GrpcGraduationType = when (this) {
        GraduationType.PROSPECTIVE -> GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE
        GraduationType.GRADUATED -> GrpcGraduationType.GRADUATION_TYPE_GRADUATED
        GraduationType.GED -> GrpcGraduationType.GRADUATION_TYPE_GED
        null -> GrpcGraduationType.GRADUATION_TYPE_UNSPECIFIED
    }
    private fun ApplicantStatus.toGrpc(): GrpcApplicantStatus = when (this) {
        ApplicantStatus.DRAFT -> GrpcApplicantStatus.APPLICANT_STATUS_DRAFT
        ApplicantStatus.SUBMITTED -> GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED
        ApplicantStatus.ARRIVAL -> GrpcApplicantStatus.APPLICANT_STATUS_ARRIVAL
        ApplicantStatus.REVIEWING -> GrpcApplicantStatus.APPLICANT_STATUS_REVIEWING
        ApplicantStatus.COMPLETED -> GrpcApplicantStatus.APPLICANT_STATUS_COMPLETED
        ApplicantStatus.CANCELED -> GrpcApplicantStatus.APPLICANT_STATUS_CANCELED
    }
