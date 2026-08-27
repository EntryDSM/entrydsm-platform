package hs.kr.entrydsm.configuration.domain.document.exception

import hs.kr.entrydsm.configuration.domain.document.FileCategory

class InvalidFileFormatException(fileName: String, category: FileCategory) :
    RuntimeException(
        "Unsupported file format for ${category.name}: $fileName " +
            "(allowed: ${category.allowedExtensions.joinToString { it.value }})"
    )
