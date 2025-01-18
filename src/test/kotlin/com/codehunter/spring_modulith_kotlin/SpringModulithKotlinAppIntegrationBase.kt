package com.codehunter.spring_modulith_kotlin

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.http.Request
import com.github.tomakehurst.wiremock.http.Response
import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ApplicationEvent
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import wiremock.org.apache.commons.io.IOUtils
import java.util.*

class WiremockInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    val log = LoggerFactory.getLogger(this::class.java)

    companion object {
//        @JvmStatic
//        @RegisterExtension
//        val wiremockServer = WireMockExtension.newInstance()
//            .options(wireMockConfig().dynamicPort().dynamicHttpsPort())
//            .configureStaticDsl(true)
//            .build();
    }

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val wiremockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wiremockServer.addMockServiceRequestListener(::requestReceived)
        wiremockServer.start()
        applicationContext.beanFactory.registerSingleton("wiremockServer", wiremockServer)
        applicationContext.addApplicationListener({ event: ApplicationEvent ->
            if (event is ContextClosedEvent) {
                wiremockServer.stop()
                log.info("WireMock server stopped")
            }
        })
        TestPropertyValues.of(
            mapOf(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" to "${wiremockServer.baseUrl()}/jwks.json",
                "spring.security.oauth2.client.provider.spring-auth0-mvc.issuer-uri" to "${wiremockServer.baseUrl()}/",
                "spring.security.oauth2.client.provider.spring-auth0-mvc.jwk-set-uri" to "${wiremockServer.baseUrl()}/jwks.json",
            )
        ).applyTo(applicationContext)

        val mockResponse = IOUtils.toString(
            ClassPathResource("auth-server-mock-response/openid-configuration.json").inputStream,
            "UTF-8"
        ).replace("https://dev-codehunter.auth0.com", wiremockServer.baseUrl())
        wiremockServer.stubFor(
            WireMock.get("/.well-known/openid-configuration")
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(mockResponse)
                )
        )
        log.info("WireMock server started")
    }

    fun requestReceived(
        inRequest: Request,
        inResponse: Response
    ) {
        log.info("WireMock request at URL: {}", inRequest.absoluteUrl)
        log.info("WireMock request headers: \n{}", inRequest.headers)
        log.info("WireMock request body: \n{}", inRequest.bodyAsString)
        log.info("WireMock response body: \n{}", inResponse.bodyAsString)
        log.info("WireMock response headers: \n{}", inResponse.headers)
    }
}

abstract class ContainerBaseTest {
    companion object {
        @Container
        @JvmStatic
        val mySQLContainer = MySQLContainer(DockerImageName.parse("mysql:8.0.33")).withReuse(true)

        @DynamicPropertySource
        @JvmStatic
        fun mySqlProperties(registry: DynamicPropertyRegistry) {
//            registry.add("spring.datasource.url" ){
//                "${mySQLContainer.jdbcUrl.replace("127.0.0.1", "localhost")}?allowPublicKeyRetrieval=true&useSSL=false"
//            }
            registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl)
            registry.add("spring.datasource.username", mySQLContainer::getUsername)
            registry.add("spring.datasource.password", mySQLContainer::getPassword)
        }

        // init mock authentication
        val rsaKey = RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .expirationTime(Date(Date().time + 60 * 1000))
            .algorithm(Algorithm("RS256"))
            .keyID("1234")
            .generate()
        val token = getSignedJwt()

        private fun getSignedJwt(): String {
            val signer = RSASSASigner(rsaKey)
            val claimsSet = JWTClaimsSet.Builder()
                .expirationTime(Date(Date().time + 60 * 1000))
                .claim("http://coundowntimer.com/roles", listOf("user"))
                .issuer("https://dev-codehunter.auth0.com/")
                .subject("auth0|604a3194414b5e007020aacd")
                .audience("https://dev-codehunter.auth0.com/api/v2/")
                .claim(
                    "scope",
                    "read:current_user update:current_user_metadata delete:current_user_metadata create:current_user_metadata create:current_user_device_credentials delete:current_user_device_credentials update:current_user_identities"
                )
                .claim("gty", "password")
                .claim("azp", "sNYVOrixNb0ZyE0WZnxvurbuOYTmX9SK")
                .build()
            val signedJWT = SignedJWT(
                JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey!!.keyID).build(), claimsSet
            )
            signedJWT.sign(signer)
            return signedJWT.serialize()
        }
    }
}