package hs.kr.entrydsm.admin.domain.port.out

import java.io.OutputStream

data class ApplicationEssayPdfs(val introduction: ByteArray?, val studyPlan: ByteArray?)

fun interface ApplicationEssayPort {
    fun render(applicantId: Long, examineeNumber: String): ApplicationEssayPdfs

    fun renderBatch(targets: List<Pair<Long, String>>): List<ApplicationEssayPdfs> =
        targets.map { (applicantId, examineeNumber) -> render(applicantId, examineeNumber) }
}

fun interface EssayPdfMergePort {
    // 지원자별 자기소개서와 학업계획서를 받은 순서대로 합친다.
    fun writeTo(documents: List<ApplicationEssayPdfs>, output: OutputStream)
}
