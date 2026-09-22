package hs.kr.entrydsm.admin.domain.port.`in`

import java.io.OutputStream

fun interface DownloadEssaysUseCase {
    fun writeTo(output: OutputStream)
}
