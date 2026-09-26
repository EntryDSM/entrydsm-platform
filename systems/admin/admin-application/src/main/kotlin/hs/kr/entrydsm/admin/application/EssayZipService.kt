package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicationEssayPort
import org.springframework.stereotype.Service
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class EssayZipService(
    private val applicantRepository: ApplicantRepository,
    private val applicationEssayPort: ApplicationEssayPort,
) : DownloadEssaysUseCase {
    override fun writeTo(output: OutputStream): Int {
        var count = 0
        ZipOutputStream(output).use { zip ->
            val usedNames = mutableSetOf<String>()
            applicantRepository.findAll().forEach { applicant ->
                count++
                val base = "${applicant.examineeNumber ?: applicant.id}_${applicant.name ?: "이름없음"}"
                    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                val pdfs = applicationEssayPort.render(applicant.id)
                pdfs.introduction?.let { zip.writeEntry(unique("${base}_자기소개서.pdf", usedNames), it) }
                pdfs.studyPlan?.let { zip.writeEntry(unique("${base}_학업계획서.pdf", usedNames), it) }
            }
        }
        return count
    }

    private fun unique(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val base = name.removeSuffix(".pdf")
        var suffix = 2
        while (!used.add("${base}_$suffix.pdf")) suffix++
        return "${base}_$suffix.pdf"
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
