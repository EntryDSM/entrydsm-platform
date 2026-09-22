package hs.kr.entrydsm.admin.domain.port.out

data class ApplicationEssayPdfs(val introduction: ByteArray?, val studyPlan: ByteArray?)

fun interface ApplicationEssayPort {
    fun render(applicantId: Long): ApplicationEssayPdfs
}
