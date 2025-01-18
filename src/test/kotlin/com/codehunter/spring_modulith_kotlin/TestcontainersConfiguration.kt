package com.codehunter.spring_modulith_kotlin

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName


@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer<*> {
        return MySQLContainer(DockerImageName.parse("mysql:latest"))
    }

}

@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun clientRegistrationRepository(
        oAuth2ClientProperties: OAuth2ClientProperties?
    ): ClientRegistrationRepository {
        val clientRegistrations =
            OAuth2ClientPropertiesMapper(oAuth2ClientProperties)
                .asClientRegistrations()
                .values.toMutableList()
        return InMemoryClientRegistrationRepository(clientRegistrations)
    }
}