package com.codehunter.spring_modulith_kotlin

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.commons.lang3.StringUtils
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.core.Authentication
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.io.IOException

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

    @Value("\${spring.security.oauth2.client.provider.spring-auth0-mvc.issuer-uri}")
    lateinit var issuer: String

    @Value("\${spring.security.oauth2.client.registration.spring-auth0-mvc.client-id}")
    lateinit var clientId: String

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
                        "/","/index.html",
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
                        "/","/index.html",
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
            .logout { it.addLogoutHandler(logoutHandler()) }
        return http.build()
    }

    private fun logoutHandler(): LogoutHandler {
        return LogoutHandler { _: HttpServletRequest, response: HttpServletResponse, _: Authentication ->
            try {
                val baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                response.sendRedirect(issuer + "v2/logout?client_id=" + clientId + "&returnTo=" + baseUrl)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
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

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "bearerAuth"
        val apiTitle = String.format("%s API", StringUtils.capitalize("Modulith Kotlin"))
        return OpenAPI()
            .addSecurityItem(SecurityRequirement().addList(securitySchemeName)) // this line make all method must be authenticated
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
            .info(Info().title(apiTitle).version("v1"))
    }

    @Bean
    fun methodPlaygroundApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("todo-kotlin")
            .pathsToMatch("/api/todos/**")
            .build()
    }

    @Bean
    fun fruitOrderingApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("fruit-ordering")
            .pathsToMatch("/api/fruit-ordering/**")
            .build()
    }
}


fun main(args: Array<String>) {
    runApplication<SpringModulithKotlinApplication>(*args)
}
