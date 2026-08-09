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
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifier
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import java.time.Instant
import java.time.LocalDate

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {
    private val publicRequestMatchers = arrayOf(
        "/actuator/health",
        "/actuator/info",
        *AuthEndpointPaths.PUBLIC.toTypedArray(),
    )

    @Bean
    fun jwtTokenGenerator(properties: JwtProperties, clock: Clock): JwtTokenGenerator =
        JwtTokenGenerator(properties.secret, properties.issuer, clock)

    @Bean
    fun jwtTokenVerifier(properties: JwtProperties, clock: Clock): JwtTokenVerifier =
        JwtTokenVerifier(properties.secret, properties.issuer, clock)

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
                    // This service exposes the token through a non-HttpOnly cookie for SPA clients.
                    // The request header must therefore contain the same token value as the cookie.
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(*AuthEndpointPaths.PUBLIC.toTypedArray())
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
                        *publicRequestMatchers,
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterAfter(CsrfCookieResponseFilter(), CsrfFilter::class.java)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}

private class CsrfCookieResponseFilter : org.springframework.web.filter.OncePerRequestFilter() {
    override fun doFilterInternal(
        request: jakarta.servlet.http.HttpServletRequest,
        response: jakarta.servlet.http.HttpServletResponse,
        filterChain: jakarta.servlet.FilterChain,
    ) {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        filterChain.doFilter(request, response)
    }
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
