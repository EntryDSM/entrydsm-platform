package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.domain.document.FileCategory

object FileReferenceId {

    fun of(category: FileCategory, id: Long): String = "${prefixOf(category)}$id"

    fun parse(category: FileCategory, value: String): Long =
        value.removePrefix(prefixOf(category)).toLongOrNull()
            ?: throw IllegalArgumentException("Invalid ${category.name.lowercase()} id: $value")

    private fun prefixOf(category: FileCategory) = "${category.name.lowercase()}_"
}
