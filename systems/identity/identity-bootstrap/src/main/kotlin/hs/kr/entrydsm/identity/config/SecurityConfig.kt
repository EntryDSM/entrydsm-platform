package hs.kr.entrydsm.identity.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hs.kr.entrydsm.identity.config.security.JwtAuthenticationEntryPoint
import hs.kr.entrydsm.identity.config.security.JwtAuthorizationDeniedHandler
import hs.kr.entrydsm.identity.config.security.JwtFilter
import hs.kr.entrydsm.identity.config.security.JwtProperties
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import java.time.Instant
import java.time.LocalDate

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {
    @Bean
    fun jwtTokenGenerator(properties: JwtProperties, clock: Clock): JwtTokenGenerator =
        JwtTokenGenerator(properties.secret, properties.issuer, clock)

    @Bean
    fun objectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .registerModule(
                SimpleModule().apply {
                    addSerializer(Instant::class.java, InstantJsonSerializer())
                    addDeserializer(Instant::class.java, InstantJsonDeserializer())
                    addSerializer(LocalDate::class.java, LocalDateJsonSerializer())
                    addDeserializer(LocalDate::class.java, LocalDateJsonDeserializer())
                },
            )

    @Bean
    fun authenticationEntryPoint(objectMapper: ObjectMapper): JwtAuthenticationEntryPoint =
        JwtAuthenticationEntryPoint(objectMapper)

    @Bean
    fun accessDeniedHandler(objectMapper: ObjectMapper): JwtAuthorizationDeniedHandler =
        JwtAuthorizationDeniedHandler(objectMapper)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtFilter: JwtFilter,
        authenticationEntryPoint: JwtAuthenticationEntryPoint,
        accessDeniedHandler: JwtAuthorizationDeniedHandler,
    ): SecurityFilterChain =
        http
            .csrf {
                it
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                        "/api/identity/v11/auth/signup",
                        "/api/identity/v11/auth/login",
                        "/api/identity/v11/auth/token",
                        "/api/identity/v11/auth/password-reset",
                    )
            }
            .cors { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api/identity/v11/auth/signup",
                        "/api/identity/v11/auth/login",
                        "/api/identity/v11/auth/token",
                        "/api/identity/v11/auth/password-reset",
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}

private class InstantJsonSerializer : JsonSerializer<Instant>() {
    override fun serialize(
        value: Instant,
        generator: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        generator.writeString(value.toString())
    }
}

private class InstantJsonDeserializer : JsonDeserializer<Instant>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): Instant = Instant.parse(parser.text)
}

private class LocalDateJsonSerializer : JsonSerializer<LocalDate>() {
    override fun serialize(
        value: LocalDate,
        generator: JsonGenerator,
        serializers: SerializerProvider,
    ) {
        generator.writeString(value.toString())
    }
}

private class LocalDateJsonDeserializer : JsonDeserializer<LocalDate>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): LocalDate = LocalDate.parse(parser.text)
}
