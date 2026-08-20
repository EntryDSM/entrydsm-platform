package hs.kr.entrydsm.identity.adapterin.web.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [Utf8ByteLengthValidator::class])
annotation class Utf8ByteLength(
    val max: Int,
    val message: String = "입력값의 바이트 길이가 허용 범위를 초과했습니다.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class Utf8ByteLengthValidator : ConstraintValidator<Utf8ByteLength, String> {
    private var max: Int = 0

    override fun initialize(annotation: Utf8ByteLength) {
        max = annotation.max
    }

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || value.toByteArray(Charsets.UTF_8).size <= max
}
