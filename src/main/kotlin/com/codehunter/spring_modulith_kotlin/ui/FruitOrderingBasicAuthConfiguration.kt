package com.codehunter.spring_modulith_kotlin.ui

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

/**
 * HTTP Basic protection for the server-rendered Fruit Ordering pages. Evaluated before the
 * OAuth2-login chain so unauthenticated requests get 401 instead of an Auth0 redirect.
 */
@Configuration
class FruitOrderingBasicAuthConfiguration {

    @Bean
    @Order(1)
    fun fruitOrderingBasicAuthFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/fruit-ordering/**")
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .httpBasic(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
        return http.build()
    }

    @Bean
    fun fruitOrderingPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun fruitOrderingUserDetailsService(
        passwordEncoder: PasswordEncoder,
        @Value("\${fruit-ordering.basic-auth.username}") username: String,
        @Value("\${fruit-ordering.basic-auth.password}") password: String,
    ): UserDetailsService {
        require(username.isNotBlank() && password.isNotBlank()) {
            "fruit-ordering.basic-auth.username and .password must not be blank"
        }
        return InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build()
        )
    }
}
