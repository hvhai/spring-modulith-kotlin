package com.codehunter.spring_modulith_kotlin.fruitordering

import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt
import com.codehunter.spring_modulith_kotlin.IntegrationBaseTest
import com.codehunter.spring_modulith_kotlin.TestContainerConfig
import com.codehunter.spring_modulith_kotlin.TestSecurityConfiguration
import com.codehunter.spring_modulith_kotlin.WiremockInitializer
import com.codehunter.spring_modulith_kotlin.eventsourcing.PaymentEvent
import com.codehunter.spring_modulith_kotlin.fruitordering_order.CreateOrderRequestDTO
import com.codehunter.spring_modulith_kotlin.fruitordering_order.OrderController
import com.codehunter.spring_modulith_kotlin.fruitordering_order.internal.JpaOrder
import com.codehunter.spring_modulith_kotlin.fruitordering_order.internal.OrderRepository
import com.codehunter.spring_modulith_kotlin.fruitordering_payment.internal.PaymentRepository
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal.JpaWarehouseProduct
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal.WarehouseProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.EnableScenarios
import org.springframework.modulith.test.Scenario
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@EnableScenarios
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@Import(value = arrayOf(TestSecurityConfiguration::class, TestContainerConfig::class))
@ActiveProfiles("integration")
@TestPropertySource(
    properties = [
        "spring.jpa.properties.hibernate.enable_lazy_load_no_trans=true"
    ]
)
class FruitOrderingIntegrationTest : IntegrationBaseTest() {
    @Autowired
    lateinit var orderController: OrderController

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var warehouseProductRepository: WarehouseProductRepository

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Test
    @WithJwt(
        json = """
            {
              "sub": "user",
              "preferred_username": "user"
            }
            """
    )
    fun `when create new order`(scenario: Scenario) {
        // given
        // Stock: Apple: 10
        val allProduct = warehouseProductRepository.findAll()

        val appleProduct = allProduct.find { it.name == "Apple" }!!
        assertThat(appleProduct.quantity).isEqualTo(10)

        // when
        // Order: Apple: 1
        val createOrderRequestDTO = CreateOrderRequestDTO(
            listOf(
                CreateOrderRequestDTO.ProductRequestDTO(appleProduct.id!!)
            )
        )
        scenario.stimulate(Runnable { orderController.createOrder(createOrderRequestDTO) })
            .andWaitForEventOfType(PaymentEvent::class.java)
            .matching({ event: PaymentEvent ->
                event.paymentEventType.equals(PaymentEvent.PaymentEventType.CREATED)
            })
            .toArriveAndVerify({ _: PaymentEvent ->
                // then
                // Order created
                val allOrderAfterCreate: List<JpaOrder> = orderRepository.findAll()
                assertThat(allOrderAfterCreate).hasSize(1)
                val orderId: String = allOrderAfterCreate[0].id!!
                val createdOrder: JpaOrder = orderRepository.findById(orderId).get()

                // with selected product
                assertThat(createdOrder.products).hasSize(1)
                val selectedOrderProduct = createdOrder.products
                    .firstOrNull { it.id == appleProduct.id }
                assertThat(selectedOrderProduct).isNotNull()

                // Stock quantity reduce 1: Apple: 9
                val updatedProduct: JpaWarehouseProduct =
                    warehouseProductRepository.findById(appleProduct.id!!).get()
                assertThat(updatedProduct.quantity).isEqualTo(9)

                // Payment created with null purchaseAt
                val payments = paymentRepository.findByOrderId(orderId)
                assertThat(payments).hasSize(1)
                assertThat(payments.first().purchaseAt).isNull()
            })

    }
}