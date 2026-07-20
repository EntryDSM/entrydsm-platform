package hs.kr.entrydsm.identity.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hs.kr.entrydsm.identity.config.security.JwtAuthenticationEntryPoint
import hs.kr.entrydsm.identity.config.security.JwtAuthorizationDeniedHandler
import hs.kr.entrydsm.identity.config.security.JwtFilter
import hs.kr.entrydsm.identity.config.security.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {
    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

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
            .csrf { it.disable() }
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
