package hs.kr.entrydsm.application.adapterin.web.dto.request

import jakarta.validation.Validation
import org.junit.Assert.assertEquals
import org.junit.Test

class EssayRequestValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun introductionAndStudyPlanAllowUpTo1600Characters() {
        val valid = "a".repeat(1600)
        val invalid = "a".repeat(1601)

        assertEquals(0, validator.validate(UpdateIntroductionRequest(valid)).size)
        assertEquals(1, validator.validate(UpdateIntroductionRequest(invalid)).size)
        assertEquals(0, validator.validate(UpdateStudyPlanRequest(valid)).size)
        assertEquals(1, validator.validate(UpdateStudyPlanRequest(invalid)).size)
    }
}
