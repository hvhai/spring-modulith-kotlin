package com.codehunter.spring_modulith_kotlin.ui

import com.codehunter.spring_modulith_kotlin.DirectlyConfiguredJwkSetUri
import com.codehunter.spring_modulith_kotlin.fruitordering_order.OrderService
import com.codehunter.spring_modulith_kotlin.fruitordering_payment.PaymentService
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseService
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Loads every security chain together to prove chain ordering and the error-dispatch behavior. */
@WebMvcTest(
    FruitOrderingUIController::class,
    properties = [
        "fruit-ordering.basic-auth.username=alice",
        "fruit-ordering.basic-auth.password=secret",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks.json",
        "spring.security.oauth2.client.provider.spring-auth0-mvc.issuer-uri=http://localhost/",
        "spring.security.oauth2.client.registration.spring-auth0-mvc.client-id=test-client",
    ],
)
@Import(DirectlyConfiguredJwkSetUri::class, FruitOrderingBasicAuthConfiguration::class)
class SecurityChainsTest(
    @Autowired val mockMvc: MockMvc
) {
    @MockkBean
    lateinit var clientRegistrationRepository: ClientRegistrationRepository

    @MockkBean
    lateinit var warehouseService: WarehouseService

    @MockkBean
    lateinit var orderService: OrderService

    @MockkBean
    lateinit var paymentService: PaymentService

    // Spring Boot forwards a 401 to /error; the OAuth2 login chain must not redirect that forward to Auth0
    @Test
    fun `error_dispatch_path_is_not_redirected_to_oauth2_login`() {
        val status = mockMvc.get("/error").andReturn().response.status
        assertNotEquals(302, status)
    }

    @Test
    fun `fruit_ordering_without_credentials_is_challenged_not_redirected`() {
        mockMvc.get("/fruit-ordering")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `other_protected_mvc_route_still_redirects_to_oauth2_login`() {
        mockMvc.get("/profile")
            .andExpect { status { is3xxRedirection() } }
    }
}
