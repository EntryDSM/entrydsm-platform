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
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.time.Instant
import java.time.LocalDate

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {
    @Value("\${security.cookies.secure:true}")
    private var secureCookies: Boolean = true

    @Value("\${security.cors.allowed-origins:}")
    private var allowedCorsOrigins: String = ""

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
    fun corsConfigurationSource(environment: Environment): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().also { source ->
            validateSecurityConfiguration(environment)
            source.registerCorsConfiguration("/**", CorsConfiguration().apply {
                allowedOrigins = allowedCorsOrigins.split(',').map(String::trim).filter(String::isNotEmpty)
                allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Requested-With")
                allowCredentials = true
                maxAge = 3600
            })
        }

    private fun validateSecurityConfiguration(environment: Environment) {
        SecurityConfigurationValidator.validate(
            secureCookies = secureCookies,
            allowedCorsOrigins = allowedCorsOrigins,
            production = environment.acceptsProfiles(Profiles.of("prod")),
        )
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtFilter: JwtFilter,
        authenticationEntryPoint: JwtAuthenticationEntryPoint,
        accessDeniedHandler: JwtAuthorizationDeniedHandler,
    ): SecurityFilterChain =
        CookieCsrfTokenRepository.withHttpOnlyFalse().also {
            it.setCookieCustomizer { cookie ->
                cookie
                    .secure(secureCookies)
                    .sameSite("Lax")
                    .path("/")
            }
        }.let { csrfTokenRepository ->
        http
            .csrf {
                it
                    .csrfTokenRepository(csrfTokenRepository)
                    // This service exposes the token through a non-HttpOnly cookie for SPA clients.
                    // The request header must therefore contain the same token value as the cookie.
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }
            .cors { }
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
}

object SecurityConfigurationValidator {
    fun validate(
        secureCookies: Boolean,
        allowedCorsOrigins: String,
        production: Boolean,
    ) {
        val origins = allowedCorsOrigins.split(',').map(String::trim).filter(String::isNotEmpty)
        require(origins.none { it == "*" }) {
            "Wildcard CORS origins are not allowed when credentials are enabled"
        }
        if (production) {
            require(secureCookies) { "Secure cookies must be enabled in the prod profile" }
            require(origins.isNotEmpty()) { "CORS origins must be configured in the prod profile" }
        }
    }
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
