package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal

import com.codehunter.spring_modulith_kotlin.eventsourcing.EventSourcingService
import com.codehunter.spring_modulith_kotlin.eventsourcing.OrderEvent
import com.codehunter.spring_modulith_kotlin.eventsourcing.WarehouseEvent
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseProductDTO
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseService
import com.codehunter.spring_modulith_kotlin.share.IdNotFoundException
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.ProductDTO
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
@Transactional
class InitData(
    private val productRepository: WarehouseProductRepository,
    private val publisher: ApplicationEventPublisher,
    private val eventSourcingService: EventSourcingService,
) : ApplicationListener<ContextRefreshedEvent?> {
    val log = LoggerFactory.getLogger(this::class.java)

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        log.info("onApplicationEvent: App STARTED")

        if (productRepository.findByName("Apple") != null) {
            log.info("Products already initialized")
            return
        }
        val product1 = JpaWarehouseProduct(null, "Apple", 10, BigDecimal(10_000))
        val product2 = JpaWarehouseProduct(null, "Orange", 0, BigDecimal(5_000))
        val product3 = JpaWarehouseProduct(null, "Lemon", 5, BigDecimal(2_000))
        val jpaProducts: List<JpaWarehouseProduct> =
            productRepository.saveAll(listOf(product1, product2, product3))
        jpaProducts.forEach {
            requireNotNull(it.id)
            eventSourcingService.addWarehouseEvent(
                WarehouseEvent(
                    listOf(it.toProductDTO()),
                    it.id!!,
                    WarehouseEvent.WarehouseEventType.ADDED
                )
            )
        }
    }
}

@Service
@Transactional
class WarehouseServiceImpl(
    private val productRepository: WarehouseProductRepository,
    private val eventSourcingService: EventSourcingService
) : WarehouseService {
    val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    override fun reserveProductForOrder(request: OrderDTO) {
        val orderId: String = request.id
        log.info("Reserve product for OrderId={}", request.id)
        val productMap: Map<String, ProductDTO> = request.products.associateBy { it.id }
        val existentProductList = productRepository.findAllById(productMap.keys)

        try {
            val updatedList = existentProductList.map(JpaWarehouseProduct::reserveForOrder)
            productRepository.saveAll(updatedList)
            log.info("[WarehouseProductPackageCompletedEvent]Products are ready for OrderId={}", orderId)
            //        applicationEventPublisher.publishEvent(new WarehouseProductPackageCompletedEvent(orderId));
            eventSourcingService.addWarehouseEvent(
                WarehouseEvent(
                    listOf(),
                    request.id,
                    WarehouseEvent.WarehouseEventType.RESERVE_COMPLETED
                )
            )
        } catch (exception: ProductOutOfStockException) {
            log.info("[WarehouseProductOutOfStockEvent]Products are out of stock for OrderId={}", orderId)
            //            applicationEventPublisher.publishEvent(new WarehouseProductOutOfStockEvent(request.orderId(), warehouseProductMapper.toProductDto(exception.getProduct())));
            eventSourcingService.addWarehouseEvent(
                WarehouseEvent(
                    listOf(exception.product.toProductDTO()),
                    request.id,
                    WarehouseEvent.WarehouseEventType.OUT_OF_STOCK
                )
            )
            return
        }
    }

    override fun allProduct(): List<WarehouseProductDTO> {
        val allJpaProduct = productRepository.findAll()
        return allJpaProduct.map { it.toDTO() }
    }

    override fun getProduct(id: String): ProductDTO {
        val jpaWarehouseProduct = productRepository.findById(id)
        if (jpaWarehouseProduct.isEmpty) {
            log.warn("Product not found, id={}", id)
            throw IdNotFoundException(String.format("Product not found, id=%s", id))
        }
        return jpaWarehouseProduct.get().toProductDTO()
    }
}

@Component
class WarehouseEventHandler(private val warehouseService: WarehouseService) {
    val log = LoggerFactory.getLogger(this::class.java)

    @ApplicationModuleListener
    fun onOrderEvent(orderEvent: OrderEvent) {
        log.info("[Warehouse]Consume Order event {}", orderEvent.orderEventType)
        when (orderEvent.orderEventType) {
            OrderEvent.OrderEventType.CREATED -> warehouseService.reserveProductForOrder(orderEvent.order)
            OrderEvent.OrderEventType.PAYMENT_REQUESTED, OrderEvent.OrderEventType.CANCELLED -> log.info("Do nothing")
        }
    }
}