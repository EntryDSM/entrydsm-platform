package hs.kr.entrydsm.admin.domain.document

/**
 * 내보내기 작업 산출물의 저장소 객체 키 규칙입니다.
 *
 * 지원자 한 명의 수험표·원서 원본 파일은 document(configuration) 서비스가 갖습니다.
 */
object DocumentNaming {

    fun keyRoot(environment: String): String {
        require(environment == "prod" || environment == "stag") { "STORAGE_ENV must be 'prod' or 'stag'" }
        return "dsm_Entry/backend/$environment/"
    }

    fun firstPassListObjectKey(exportJobId: String, environment: String): String =
        "${keyRoot(environment)}first-pass/first_pass_$exportJobId.xlsx"

    fun admissionFileObjectKey(exportJobId: String, environment: String): String =
        "${keyRoot(environment)}admission-file/admission_file_$exportJobId.xlsx"

    fun applicationChecklistObjectKey(exportJobId: String, environment: String): String =
        "${keyRoot(environment)}application-checklist/application_checklist_$exportJobId.xlsx"

    fun essaysObjectKey(exportJobId: String, environment: String): String =
        "${keyRoot(environment)}essays/essays_$exportJobId.pdf"

    /** 1차 합격자 수험표를 한 시트에 이어 그린 xlsx 하나입니다. */
    fun admissionTicketBundleObjectKey(exportJobId: String, environment: String): String =
        "${keyRoot(environment)}admission-ticket/admission_tickets_$exportJobId.xlsx"
}
