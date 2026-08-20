package hs.kr.entrydsm.identity.config.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String,
)
