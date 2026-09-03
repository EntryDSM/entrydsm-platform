package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.domain.document.FileCategory

class InvalidFileReferenceIdException(category: FileCategory, value: String) :
    RuntimeException("Invalid ${category.name.lowercase()} id: $value")

object FileReferenceId {

    fun of(category: FileCategory, id: Long): String = "${prefixOf(category)}$id"

    fun parse(category: FileCategory, value: String): Long {
        val prefix = prefixOf(category)
        return value.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.toLongOrNull()
            ?: throw InvalidFileReferenceIdException(category, value)
    }

    private fun prefixOf(category: FileCategory) = "${category.name.lowercase()}_"
}
