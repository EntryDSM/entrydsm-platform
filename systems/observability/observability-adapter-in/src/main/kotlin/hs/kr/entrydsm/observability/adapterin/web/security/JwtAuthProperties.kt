package hs.kr.entrydsm.observability.adapterin.web.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/**
 * identity의 auth.jwt.secret/issuer와 설정 키 이름을 통일해, 정식 인증이 병합되면
 * 같은 시크릿을 가리키도록 맞추기만 하면 되게 한다.
 */
@Component
@ConfigurationProperties(prefix = "auth.jwt")
@Validated
class JwtAuthProperties {
    /** 비어 있으면 기동에 실패한다. 알려진 기본 시크릿으로 토큰이 검증되는 상태를 만들지 않기 위한 것. */
    @field:NotBlank
    lateinit var secret: String

    @field:NotBlank
    lateinit var issuer: String
}
