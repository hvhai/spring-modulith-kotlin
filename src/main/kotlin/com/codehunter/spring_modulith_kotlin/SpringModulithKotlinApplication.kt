package com.codehunter.spring_modulith_kotlin

import com.codehunter.spring_modulith_kotlin.share.ErrorCodes
import com.codehunter.spring_modulith_kotlin.share.IdNotFoundException
import com.codehunter.spring_modulith_kotlin.share.ResponseFormatter
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@SpringBootApplication
class SpringModulithKotlinApplication {
    @Autowired
    lateinit var environment: Environment

    @PostConstruct
    fun printConfigProperties() {
        val propertyKeys = listOf<String>(
            "management.endpoints.web.exposure.include",
            "management.endpoint.env.show-values",
            "management.tracing.sampling.probability",
            "management.tracing.enabled",
            "management.zipkin.tracing.endpoint",
            "spring.datasource.url",
            "spring.h2.console.enabled",
            "spring.h2.console.path",
            "spring.h2.console.settings.web-allow-others",
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            "spring.security.oauth2.client.provider.spring-auth0-mvc.issuer-uri",
            "spring.security.oauth2.client.provider.spring-auth0-mvc.jwk-set-uri",
        )
        propertyKeys.forEach { println(" $it  = ${environment.getProperty(it)}") }
    }


}

@Configuration
@EnableWebSecurity(debug = false)
class DirectlyConfiguredJwkSetUri {
    @Value("\${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    lateinit var jwkSetUri: String

    @Bean
    @Throws(java.lang.Exception::class)
    @Order(0)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests({
                it
                    .requestMatchers(
                        "/h2-console/**", "/actuator/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/v3/**",
                    )
                    .permitAll()
                    .anyRequest().authenticated()
            })
            .oauth2ResourceServer { oauth2: OAuth2ResourceServerConfigurer<HttpSecurity?> ->
                oauth2
                    .jwt({ it.jwkSetUri(jwkSetUri) })
            }
            /*
            If you use Spring MVC’s CORS support, you can omit specifying the CorsConfigurationSource
            and Spring Security uses the CORS configuration provided to Spring MVC:
            link: https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html
             */
            .cors { }
            .csrf { it.disable() }
            .headers { it.frameOptions(HeadersConfigurer<HttpSecurity>.FrameOptionsConfig::disable) }
        return http.build()
    }

    @Bean
    @Throws(java.lang.Exception::class)
    @Order(1)
    fun filterChainMvc(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests({
                it
                    .requestMatchers(
                        "/h2-console/**", "/actuator/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/v3/**",
                        "/api/**"
                    )
                    .permitAll()
                    .anyRequest().authenticated()
            })
            .oauth2Login(Customizer.withDefaults())
            .oauth2ResourceServer { it.disable() }
            .csrf { it.disable() }
            .headers { it.frameOptions(HeadersConfigurer<HttpSecurity>.FrameOptionsConfig::disable) }
        return http.build()
    }
}

@Configuration
class WebConfig : WebMvcConfigurer {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOrigins("*")
                    .allowedMethods("*")
            }
        }
    }
}



fun main(args: Array<String>) {
    runApplication<SpringModulithKotlinApplication>(*args)
}
