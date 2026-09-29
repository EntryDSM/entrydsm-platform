package hs.kr.entrydsm.admin.domain.port.out

data class ApplicationEssayPdfs(val introduction: ByteArray?, val studyPlan: ByteArray?)

fun interface ApplicationEssayPort {
    fun render(applicantId: Long, examineeNumber: String): ApplicationEssayPdfs

    fun renderBatch(targets: List<Pair<Long, String>>): List<ApplicationEssayPdfs> =
        targets.map { (applicantId, examineeNumber) -> render(applicantId, examineeNumber) }
}
