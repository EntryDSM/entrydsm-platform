package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.GuardianRelation
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Entity
@Table(name = "applicants")
open class ApplicantJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "account_id", nullable = false)
    var accountId: Long = 0,

    @Column(name = "photo_file_id")
    var photoFileId: Long? = null,

    @Column(name = "name", length = 20)
    var name: String? = null,

    @Column(name = "phone_number", length = 16)
    var phoneNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    var gender: Gender? = null,

    @Column(name = "birthdate")
    var birthdate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "special_admission_type", nullable = false, length = 32)
    var specialAdmissionType: SpecialAdmissionType = SpecialAdmissionType.NONE,

    @Enumerated(EnumType.STRING)
    @Column(name = "admission_type", length = 16)
    var admissionType: AdmissionType? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "region", length = 16)
    var region: Region? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "graduation_type", length = 16)
    var graduationType: GraduationType? = null,

    @Column(name = "graduation_date")
    var graduationDate: LocalDate? = null,

    @Column(name = "guardian_name", length = 20)
    var guardianName: String? = null,

    @Column(name = "guardian_phone_number", length = 16)
    var guardianPhoneNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "guardian_gender", length = 10)
    var guardianGender: Gender? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "guardian_relation", length = 10)
    var guardianRelation: GuardianRelation? = null,

    @Column(name = "address_base", length = 255)
    var addressBase: String? = null,

    @Column(name = "address_detail", length = 255)
    var addressDetail: String? = null,

    @Column(name = "zip_code", length = 10)
    var zipCode: String? = null,

    @Column(name = "introduction", columnDefinition = "TEXT")
    var introduction: String? = null,

    @Column(name = "study_plan", columnDefinition = "TEXT")
    var studyPlan: String? = null,

    @Column(name = "total_score")
    var totalScore: Double? = null,

    @Column(name = "total_score_updated_at")
    var totalScoreUpdatedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToOne(mappedBy = "applicant", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    open var middleSchoolInfo: MiddleSchoolInfoJpaEntity? = null,

    @OneToOne(mappedBy = "applicant", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    open var academicRecord: AcademicRecordJpaEntity? = null,

    @OneToMany(mappedBy = "applicant", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var passResults: MutableList<PassResultJpaEntity> = mutableListOf(),
) {
    fun toDomain(): Applicant =
        Applicant(
            id = requireNotNull(id),
            accountId = accountId,
            photoFileId = photoFileId,
            name = name,
            phoneNumber = phoneNumber,
            gender = gender,
            birthdate = birthdate,
            specialAdmissionType = specialAdmissionType,
            admissionType = admissionType,
            region = region,
            graduationType = graduationType,
            graduationDate = graduationDate?.let { YearMonth.from(it) },
            guardianName = guardianName,
            guardianPhoneNumber = guardianPhoneNumber,
            guardianGender = guardianGender,
            guardianRelation = guardianRelation,
            addressBase = addressBase,
            addressDetail = addressDetail,
            zipCode = zipCode,
            introduction = introduction,
            studyPlan = studyPlan,
            middleSchoolInfo = middleSchoolInfo?.toDomain(),
            academicRecord = academicRecord?.toDomain(),
            totalScore = totalScore,
            totalScoreUpdatedAt = totalScoreUpdatedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    fun updateFrom(domain: Applicant) {
        accountId = domain.accountId
        photoFileId = domain.photoFileId
        name = domain.name
        phoneNumber = domain.phoneNumber
        gender = domain.gender
        birthdate = domain.birthdate
        specialAdmissionType = domain.specialAdmissionType
        admissionType = domain.admissionType
        region = domain.region
        graduationType = domain.graduationType
        graduationDate = domain.graduationDate?.atDay(1)
        guardianName = domain.guardianName
        guardianPhoneNumber = domain.guardianPhoneNumber
        guardianGender = domain.guardianGender
        guardianRelation = domain.guardianRelation
        addressBase = domain.addressBase
        addressDetail = domain.addressDetail
        zipCode = domain.zipCode
        introduction = domain.introduction
        studyPlan = domain.studyPlan
        totalScore = domain.totalScore
        totalScoreUpdatedAt = domain.totalScoreUpdatedAt
        updatedAt = domain.updatedAt
        updateMiddleSchoolInfo(domain.middleSchoolInfo)
        updateAcademicRecord(domain.academicRecord)
    }

    private fun updateMiddleSchoolInfo(domain: MiddleSchoolInfo?) {
        middleSchoolInfo = domain?.let {
            (middleSchoolInfo ?: MiddleSchoolInfoJpaEntity(applicant = this)).apply {
                updateFrom(it)
                applicant = this@ApplicantJpaEntity
            }
        }
    }

    private fun updateAcademicRecord(domain: AcademicRecord?) {
        academicRecord = domain?.let {
            (academicRecord ?: AcademicRecordJpaEntity(applicant = this)).apply {
                updateFrom(it)
                applicant = this@ApplicantJpaEntity
            }
        }
    }

    companion object {
        fun from(domain: Applicant): ApplicantJpaEntity =
            ApplicantJpaEntity().apply {
                updateFrom(domain)
                createdAt = domain.createdAt
            }
    }
}
