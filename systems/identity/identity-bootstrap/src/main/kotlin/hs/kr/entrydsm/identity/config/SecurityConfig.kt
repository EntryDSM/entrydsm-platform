package hs.kr.entrydsm.identity.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenGenerator
import hs.kr.entrydsm.identity.application.security.jwt.JwtTokenVerifier
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import hs.kr.entrydsm.identity.config.edge.EdgeAccessFilter
import hs.kr.entrydsm.identity.config.edge.EdgeContract
import hs.kr.entrydsm.identity.config.edge.EdgeProperties
import hs.kr.entrydsm.identity.config.security.JwtAuthenticationEntryPoint
import hs.kr.entrydsm.identity.config.security.JwtAuthorizationDeniedHandler
import hs.kr.entrydsm.identity.config.security.JwtFilter
import hs.kr.entrydsm.identity.config.security.JwtProperties
import jakarta.servlet.DispatcherType
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.access.DelegatingAccessDeniedHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfException
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 프로세스 전체의 보안 체인이다. gateway 가 앞단에서 하던 CORS·CSRF·인증을 여기서 한다.
 *
 * - identity 경로만 Spring Security 가 인증을 요구한다.
 * - 나머지 모듈 경로는 통과시키고, 엣지가 채운 신뢰 헤더를 보고 각 모듈 인터셉터가 권한을 판단한다.
 *   모듈은 Spring Security 주체를 만들지 않으므로 여기서 인증을 요구하면 공개 API 까지 401 이 된다.
 * - 라우팅하던 경로가 아니면 [EdgeAccessFilter] 가 먼저 404 로 끊는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class, EdgeProperties::class)
class SecurityConfig {
    @Value("\${security.cookies.secure:true}")
    private var secureCookies: Boolean = true

    private val publicRequestMatchers = arrayOf(
        *EdgeContract.EXPOSED_PATHS.toTypedArray(),
        "/actuator/health/**",
        *EdgeContract.PUBLIC_PATHS.toTypedArray(),
    )

    @Bean
    fun jwtTokenGenerator(properties: JwtProperties, clock: Clock): JwtTokenGenerator =
        JwtTokenGenerator(properties.secret, properties.issuer, clock)

    @Bean
    fun jwtTokenVerifier(properties: JwtProperties, clock: Clock): JwtTokenVerifier =
        JwtTokenVerifier(properties.secret, properties.issuer, clock)

    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper()
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

    /** CSRF 거부는 gateway 와 같은 형태(403 text/plain)로 두고, 그 밖의 거부는 identity 형식 JSON 으로 둔다. */
    @Bean
    fun accessDeniedHandler(objectMapper: ObjectMapper): AccessDeniedHandler =
        DelegatingAccessDeniedHandler(
            linkedMapOf<Class<out AccessDeniedException>, AccessDeniedHandler>(
                CsrfException::class.java to
                    AccessDeniedHandler { _, response, _ ->
                        response.status = HTTP_FORBIDDEN
                        response.contentType = "text/plain;charset=UTF-8"
                        response.characterEncoding = StandardCharsets.UTF_8.name()
                        response.writer.write("Access Denied")
                        response.writer.flush()
                    },
            ),
            JwtAuthorizationDeniedHandler(objectMapper),
        )

    @Bean
    fun corsConfigurationSource(edgeProperties: EdgeProperties): CorsConfigurationSource {
        val cors = edgeProperties.cors
        val configuration = CorsConfiguration().apply {
            allowedOrigins = cors.allowedOrigins
            allowedMethods = cors.allowedMethods
            allowedHeaders = cors.allowedHeaders
            exposedHeaders = cors.exposedHeaders
            allowCredentials = cors.allowCredentials
            maxAge = cors.maxAgeSeconds
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }

    private fun csrfTokenRepository(): CsrfTokenRepository =
        CookieCsrfTokenRepository().apply {
            setCookieCustomizer { cookie ->
                cookie.secure(secureCookies).httpOnly(true).sameSite("Lax").path("/")
            }
        }

    private fun validateSecurityConfiguration(environment: Environment) {
        SecurityConfigurationValidator.validate(
            secureCookies = secureCookies,
            production = environment.acceptsProfiles(Profiles.of("prod")),
        )
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        edgeAccessFilter: EdgeAccessFilter,
        jwtFilter: JwtFilter,
        corsConfigurationSource: CorsConfigurationSource,
        authenticationEntryPoint: JwtAuthenticationEntryPoint,
        accessDeniedHandler: AccessDeniedHandler,
        environment: Environment,
    ): SecurityFilterChain {
        validateSecurityConfiguration(environment)
        return http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf {
                it.csrfTokenRepository(csrfTokenRepository())
                    // 평문 토큰이어야 /csrf 응답 값과 쿠키 값이 같다. SPA 가 그 값을 헤더로 돌려준다.
                    .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers(AuthEndpointPaths.PASS_POPUP)
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it
                    // 비동기(SSE)·오류 디스패치까지 인가를 다시 걸면 이미 열린 스트림에 401 을 쓰려 한다.
                    .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                    .requestMatchers(*publicRequestMatchers).permitAll()
                    .requestMatchers("${EdgeContract.IDENTITY_PREFIX}/**").authenticated()
                    .anyRequest().permitAll()
            }
            .addFilterAfter(edgeAccessFilter, CsrfFilter::class.java)
            .addFilterAfter(jwtFilter, EdgeAccessFilter::class.java)
            .build()
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}

object SecurityConfigurationValidator {
    fun validate(
        secureCookies: Boolean,
        production: Boolean,
    ) {
        if (production) {
            require(secureCookies) { "Secure cookies must be enabled in the prod profile" }
        }
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
