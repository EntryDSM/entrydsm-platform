package hs.kr.entrydsm.admin.domain.port.out

import java.io.OutputStream

fun interface ApplicationEssayPort {
    // 두 본문이 모두 비어 있으면 null을 반환한다.
    fun render(applicantId: Long, examineeNumber: String): ByteArray?

    // 입력한 지원자 순서대로 통합 서식을 만든다.
    fun renderBatch(targets: List<Pair<Long, String>>): List<ByteArray?> =
        targets.map { (applicantId, examineeNumber) -> render(applicantId, examineeNumber) }
}

fun interface EssayPdfMergePort {
    // 두 본문을 함께 담은 지원자별 서식 3을 받은 순서대로 합친다.
    fun writeTo(documents: List<ByteArray?>, output: OutputStream)
}
