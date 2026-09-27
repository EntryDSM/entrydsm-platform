package hs.kr.entrydsm.gateway.adapterin.configuration

import org.springframework.cloud.gateway.config.GlobalCorsProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SecurityWebConfig {

    @Bean
    fun corsWebFilter(corsProperties: GlobalCorsProperties): CorsWebFilter =
        CorsWebFilter(
            UrlBasedCorsConfigurationSource().apply {
                registerCorsConfiguration("/**", corsProperties.corsConfigurations.getValue("/**"))
            },
        )

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        @Value("\${gateway.security.secure-cookies:true}") secureCookies: Boolean,
    ): SecurityWebFilterChain {
        val csrfTokenRepository =
            CookieServerCsrfTokenRepository().also {
                it.setCookieName("XSRF-TOKEN")
                it.setHeaderName("X-XSRF-TOKEN")

                it.setCookieCustomizer { cookie ->
                    cookie
                        .secure(secureCookies)
                        .httpOnly(true)
                        .sameSite("Lax")
                        .path("/")
                }
            }

        return http
            .csrf {
                it
                    .csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(
                        ServerCsrfTokenRequestAttributeHandler(),
                    )
                    .requireCsrfProtectionMatcher(csrfProtectionMatcher())
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeExchange {
                it.anyExchange().permitAll()
            }
            .build()
    }

    private fun csrfProtectionMatcher(): ServerWebExchangeMatcher {
        val excludedMatchers = OrServerWebExchangeMatcher(
            PathPatternParserServerWebExchangeMatcher("/api/identity/v11/auth/pass/popup"),
            PathPatternParserServerWebExchangeMatcher("/api/identity/v11/auth/logout"),
        )

        return ServerWebExchangeMatcher { exchange ->
            val method = exchange.request.method
            val requiresCsrf = method !in SAFE_METHODS

            if (!requiresCsrf) {
                ServerWebExchangeMatcher.MatchResult.notMatch()
            } else {
                excludedMatchers.matches(exchange)
                    .flatMap { popup ->
                        if (popup.isMatch) {
                            ServerWebExchangeMatcher.MatchResult.notMatch()
                        } else {
                            ServerWebExchangeMatcher.MatchResult.match()
                        }
                    }
            }
        }
    }

    private companion object {
        val SAFE_METHODS = setOf(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
            HttpMethod.TRACE,
        )
    }
}
