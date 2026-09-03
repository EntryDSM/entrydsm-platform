package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region

/**
 * 수험표에 인쇄되는 값만 추린 모델입니다.
 *
 * @property admissionYear 입학 학년도. 지원자 정보가 아니라 전형 설정에서 주입한다
 * @property photoDataUri 증명사진. 없으면 사진 칸을 빈 칸으로 인쇄한다
 */
data class AdmissionTicket(
    val admissionYear: Int,
    val receiptNumber: Int,
    val examineeNumber: String?,
    val name: String,
    val schoolName: String,
    val region: Region,
    val admissionType: AdmissionType,
    val photoDataUri: String? = null,
) {
    companion object {
        /**
         * 지원자로부터 수험표를 만듭니다. 수험 번호는 아직 없을 수 있습니다.
         */
        fun of(
            applicant: Applicant,
            admissionYear: Int,
            photoDataUri: String? = null,
        ): AdmissionTicket = AdmissionTicket(
            admissionYear = admissionYear,
            receiptNumber = applicant.receiptNumber,
            examineeNumber = applicant.examineeNumber,
            name = applicant.name,
            schoolName = applicant.schoolName,
            region = applicant.region,
            admissionType = applicant.admissionType,
            photoDataUri = photoDataUri,
        )
    }
}
