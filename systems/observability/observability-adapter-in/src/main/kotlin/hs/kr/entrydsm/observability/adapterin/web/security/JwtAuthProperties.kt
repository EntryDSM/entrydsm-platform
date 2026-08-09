package hs.kr.entrydsm.observability.adapterin.web.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * identity의 auth.jwt.secret/issuer와 설정 키 이름을 통일해, 정식 인증이 병합되면
 * 같은 시크릿을 가리키도록 맞추기만 하면 되게 한다.
 */
@Component
@ConfigurationProperties(prefix = "auth.jwt")
class JwtAuthProperties {
    lateinit var secret: String
    lateinit var issuer: String
}
