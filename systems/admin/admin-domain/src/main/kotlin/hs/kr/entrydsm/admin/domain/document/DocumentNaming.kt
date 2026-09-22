package hs.kr.entrydsm.admin.domain.document

/**
 * 내보내기 작업 산출물의 저장소 객체 키 규칙입니다.
 *
 * 지원자 한 명의 수험표·원서 원본 파일은 document(configuration) 서비스가 갖습니다.
 */
object DocumentNaming {

    const val KEY_ROOT = "dsm_Entry/Backend/"

    fun applicantListObjectKey(exportJobId: String): String =
        "${KEY_ROOT}applicant-list/applicants_$exportJobId.xlsx"

    fun firstPassListObjectKey(exportJobId: String): String =
        "${KEY_ROOT}first-pass/first_pass_$exportJobId.xlsx"

    fun admissionFileObjectKey(exportJobId: String): String =
        "${KEY_ROOT}admission-file/admission_file_$exportJobId.xlsx"

    /** 1차 합격자 수험표를 한 장씩 이어 붙인 PDF 하나입니다. */
    fun admissionTicketBundleObjectKey(exportJobId: String): String =
        "${KEY_ROOT}admission-ticket/admission_tickets_$exportJobId.pdf"
}
