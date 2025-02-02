package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse

import com.codehunter.spring_modulith_kotlin.MoudulithBaseTest
import com.codehunter.spring_modulith_kotlin.TestContainerConfig
import com.codehunter.spring_modulith_kotlin.TestSecurityConfiguration
import com.codehunter.spring_modulith_kotlin.WiremockInitializer
import com.codehunter.spring_modulith_kotlin.eventsourcing.WarehouseEvent
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal.JpaWarehouseProduct
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal.WarehouseProductRepository
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.OrderStatus
import com.codehunter.spring_modulith_kotlin.share.ProductDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.util.function.Consumer
import kotlin.test.assertEquals

@ApplicationModuleTest(
    mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@Import(value = arrayOf(TestSecurityConfiguration::class, TestContainerConfig::class))
@ActiveProfiles("integration")
class WarehouseDirectDependenciesModuleTest : MoudulithBaseTest() {
    @Autowired
    lateinit var warehouseService: WarehouseService

    @Autowired
    lateinit var warehouseProductRepository: WarehouseProductRepository

    @Test
    fun `when reserve a product for order then product quantity  in stock decrease by 1`(scenario: Scenario) {
        // given
        // Stock: Apple: 10
        val allProduct: List<JpaWarehouseProduct> = warehouseProductRepository.findAll()
        assertThat(allProduct).hasSize(3)
        val warehouseProductDTO1: JpaWarehouseProduct = allProduct.find { it.name == "Apple" }!!
        assertEquals(10, warehouseProductDTO1.quantity)

        // when
        val productDTO = ProductDTO(
            warehouseProductDTO1.id!!,
            warehouseProductDTO1.name,
            warehouseProductDTO1.price
        )
        val orderDTO = OrderDTO(
            "1",
            OrderStatus.IN_PRODUCT_PREPARE,
            null,
            null,
            mutableSetOf(productDTO)
        )

        // then
        scenario.stimulate(Runnable { warehouseService.reserveProductForOrder(orderDTO) })
            .andWaitForEventOfType(WarehouseEvent::class.java)
            .matching({
                it.orderId == orderDTO.id
                        && it.warehouseEventType == WarehouseEvent.WarehouseEventType.RESERVE_COMPLETED
            })
            .toArriveAndVerify(Consumer<WarehouseEvent> { _: WarehouseEvent ->
                // Stock: Apple: 9
                val updatedProduct = warehouseProductRepository.findById(warehouseProductDTO1.id!!).get()
                assertThat(updatedProduct.quantity).isEqualTo(9)
            })
    }
}