package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.UpdateAdmissionQuotaCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.AdmissionQuota
import hs.kr.entrydsm.admin.domain.port.`in`.ReadAdmissionQuotaUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateAdmissionQuotaUseCase
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AdmissionQuotaService(
    private val admissionQuotaRepository: AdmissionQuotaRepository,
    private val clock: Clock,
) : ReadAdmissionQuotaUseCase,
    UpdateAdmissionQuotaUseCase {

    override fun findCurrent(): AdmissionQuota =
        admissionQuotaRepository.find()
            ?: throw AdminDomainException(ErrorCode.ADMISSION_QUOTA_NOT_FOUND)

    @Transactional
    override fun update(command: UpdateAdmissionQuotaCommand): AdmissionQuota =
        admissionQuotaRepository.save(
            AdmissionQuota(
                quotas = command.quotas,
                updatedAt = Instant.now(clock),
                updatedBy = command.updatedBy,
            ),
        )
}
