package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse

import com.codehunter.spring_modulith_kotlin.ContainerBaseMoudulithTest
import com.codehunter.spring_modulith_kotlin.TestInfra
import com.codehunter.spring_modulith_kotlin.TestSecurityConfiguration
import com.codehunter.spring_modulith_kotlin.WiremockInitializer
import com.codehunter.spring_modulith_kotlin.eventsourcing.EventSourcingService
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal.JpaWarehouseProduct
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal.WarehouseProductRepository
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.OrderStatus
import com.codehunter.spring_modulith_kotlin.share.ProductDTO
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.modulith.test.Scenario
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.test.assertEquals


@ApplicationModuleTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = arrayOf(WiremockInitializer::class))
@ActiveProfiles("integration")
@Import(value = arrayOf(TestSecurityConfiguration::class, TestInfra::class))
class WarehouseStandaloneIntegrationTest : ContainerBaseMoudulithTest() {
    @Autowired
    lateinit var warehouseService: WarehouseService

    @Autowired
    lateinit var warehouseProductRepository: WarehouseProductRepository

    @MockitoBean
    lateinit var eventSourcingService: EventSourcingService

//    @BeforeEach
//    fun init() {
//        every { eventSourcingService.addWarehouseEvent(any()) }
//            .answers { println("addWarehouseEvent") }
//    }

    @Test
    fun reserveProductForOrder(scenario: Scenario?) {
        // given
        // Stock: Apple: 10
        val allProduct: List<JpaWarehouseProduct> = warehouseProductRepository.findAll()
        Assertions.assertThat<JpaWarehouseProduct>(allProduct).hasSize(3)
        val warehouseProductDTO1: JpaWarehouseProduct = allProduct.find { it.name == "Apple" }!!
        assertEquals(10, warehouseProductDTO1.quantity)

        // Tech debt: Mocking eventSourcingService with doNothing
//        val warehouseEvent = slot<WarehouseEvent>()
//        every { eventSourcingService.addWarehouseEvent(capture(warehouseEvent)) }
//            .answers { println("addWarehouseEvent") }
//        Mockito.doNothing().`when`(eventSourcingService).addWarehouseEvent(
//            nullable(WarehouseEvent::class.java)
//        )
//        Mockito.doNothing().`when`(eventSourcingService).addWarehouseEvent(Mockito.any(WarehouseEvent::class.java))

        // when
        val productDTO: ProductDTO = ProductDTO(
            warehouseProductDTO1.id!!,
            warehouseProductDTO1.name,
            warehouseProductDTO1.price
        )
        val orderDTO: OrderDTO = OrderDTO(
            "1",
            OrderStatus.IN_PRODUCT_PREPARE,
            null,
            null,
            mutableSetOf(productDTO)
        )
        warehouseService.reserveProductForOrder(orderDTO)

        // then
        // Stock: Apple: 9
        val updatedProduct: JpaWarehouseProduct =
            warehouseProductRepository.findById(warehouseProductDTO1.id!!).get()
        assertEquals(9, updatedProduct.quantity)
//        verify(eventSourcingService, times(1)).addWarehouseEvent(any())
//        verify(exactly = 1) { eventSourcingService.addWarehouseEvent(any()) }
//        assertEquals(orderDTO.id, warehouseEvent.captured.orderId)
//        assertEquals(WarehouseEvent.WarehouseEventType.RESERVE_COMPLETED, warehouseEvent.captured.warehouseEventType)
    }
}
