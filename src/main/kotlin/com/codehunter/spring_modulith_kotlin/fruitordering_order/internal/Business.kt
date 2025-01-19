package com.codehunter.spring_modulith_kotlin.fruitordering_order.internal

import com.codehunter.spring_modulith_kotlin.eventsourcing.EventSourcingService
import com.codehunter.spring_modulith_kotlin.eventsourcing.OrderEvent
import com.codehunter.spring_modulith_kotlin.eventsourcing.PaymentEvent
import com.codehunter.spring_modulith_kotlin.eventsourcing.WarehouseEvent
import com.codehunter.spring_modulith_kotlin.fruitordering_order.CreateOrderRequestDTO
import com.codehunter.spring_modulith_kotlin.fruitordering_order.OrderService
import com.codehunter.spring_modulith_kotlin.share.*
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderProductRepository: OrderProductRepository,
    private val eventSourcingService: EventSourcingService
) : OrderService {
    val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    override fun createOrder(createOrderRequest: CreateOrderRequestDTO, user: UserDTO): OrderDTO {
        val newOrder = createJpaOrder(createOrderRequest)
        val result: OrderDTO = newOrder.toDTO()
        eventSourcingService.addOrderEvent(OrderEvent(result, OrderEvent.OrderEventType.CREATED))
        return result
    }

    override fun allOrders(): List<OrderDTO> {
        val allOrders = orderRepository.findAll()
        return allOrders.map { it.toDTO() }
    }

    override fun getOrder(id: String): OrderDTO {
        val orderOptional = orderRepository.findById(id)
        if (orderOptional.isEmpty) {
            log.warn("Order not found, id={}", id)
            throw IdNotFoundException(String.format("Order not found, id=%s", id))
        }
        return orderOptional.get().toDTO()
    }

    @Transactional
    fun createJpaOrder(createOrderRequest: CreateOrderRequestDTO): JpaOrder {
        val products = createOrderRequest.products
            .map { productDTO ->
                val product = orderProductRepository.findById(productDTO.id)
                if (product.isPresent) {
                    return@map product.get()
                }
                null
            }
            .filterNotNull()
            .toMutableSet()

        val order = JpaOrder(products)
        val newOrder = orderRepository.save(order)
        return newOrder
    }
}


@Component
class OrderModuleEventHandler(
    private val productRepository: OrderProductRepository,
    private val orderRepository: OrderRepository,
    private val orderPaymentRepository: OrderPaymentRepository,
    private val eventSourcingService: EventSourcingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    fun onWarehouseProductCreateEvent(event: ProductDTO) {
        log.info(
            "On WarehouseProductCreateEvent, Product id={}, name={}, price={}",
            event.id,
            event.name,
            event.price
        )
        val product = JpaOrderProduct(event.id, event.name, event.price)
        productRepository.save(product)
    }

    fun onWarehouseProductPackageCompletedEvent(orderId: String) {
        log.info("On WarehouseProductPackageCompletedEvent, Order orderId={}", orderId)
        val orderOptional = orderRepository.findById(orderId)
        if (orderOptional.isEmpty) {
            log.error("Order with orderId={} not found", orderId)
            return
        }
        val order = orderOptional.get()
        val updatedOrder = orderRepository.save(order.registerForPayment())
        //        paymentService.createPayment(new PaymentService.CreatePaymentRequest(orderId, updatedOrder.getTotalAmount()));
        eventSourcingService.addOrderEvent(
            OrderEvent(
                updatedOrder.toDTO(),
                OrderEvent.OrderEventType.PAYMENT_REQUESTED
            )
        )
    }

    fun onWarehouseProductOutOfStockEvent(orderId: String, product: ProductDTO?) {
        log.info("On WarehouseProductOutOfStockEvent, Order orderId={}", orderId)
        val orderOptional = orderRepository.findById(orderId)
        if (orderOptional.isEmpty) {
            log.error("Order with orderId={} not found", orderId)
            return
        }
        val order = orderOptional.get()
        orderRepository.save(order.cancel())
//        eventSourcingService.addNotificationEvent(
//            NotificationEvent(
//                orderId,
//                NotificationEvent.NotificationEventType.ORDER_CANCELLED,
//                "Order canceled because product out of stock"
//            )
//        )
        log.info("On WarehouseProductOutOfStockEvent, Order orderId={} change status to CANCELED", orderId)
    }

    fun onPaymentCreatedEvent(event: PaymentDTO) {
        val orderId: String = event.orderId
        log.info("On PaymentCreatedEvent, Order orderId={}", orderId)
        val orderOptional = orderRepository.findById(orderId)
        if (orderOptional.isEmpty) {
            log.error("Order with orderId={} not found", orderId)
            return
        }
        val order = orderOptional.get()
        val jpaOrderPayment = orderPaymentRepository.save(JpaOrderPayment(event.id, order, event.totalAmount))
        orderRepository.save(order.waitingForPayment(jpaOrderPayment))
        log.info("On PaymentCreatedEvent, Order orderId={} change status to WAITING_FOR_PAYMENT", orderId)
    }

    fun onPaymentPurchasedEvent(event: PaymentDTO) {
        val orderId: String = event.orderId
        log.info("On PaymentPurchasedEvent, Order orderId={}", orderId)
        val orderOptional = orderRepository.findById(orderId)
        if (orderOptional.isEmpty) {
            log.error("Order with orderId={} not found", orderId)
            return
        }
        val order = orderOptional.get()
        orderRepository.save(order.finish())

//        eventSourcingService.addNotificationEvent(
//            NotificationEvent(
//                orderId,
//                NotificationEvent.NotificationEventType.ORDER_COMPLETED,
//                "Order completed"
//            )
//        )
        log.info("On PaymentPurchasedEvent, Order orderId={} change status to DONE", orderId)
    }

    @ApplicationModuleListener
    fun onWarehouseEvent(warehouseEvent: WarehouseEvent) {
        log.info("[Order]Consume Warehouse event {}", warehouseEvent.warehouseEventType)
        when (warehouseEvent.warehouseEventType) {
            WarehouseEvent.WarehouseEventType.OUT_OF_STOCK -> onWarehouseProductOutOfStockEvent(
                warehouseEvent.orderId,
                warehouseEvent.products.first()
            )

            WarehouseEvent.WarehouseEventType.RESERVE_COMPLETED -> onWarehouseProductPackageCompletedEvent(
                warehouseEvent.orderId
            )

            WarehouseEvent.WarehouseEventType.ADDED -> onWarehouseProductCreateEvent(
                warehouseEvent.products.first()
            )
        }
    }

    @ApplicationModuleListener
    fun onPaymentEvent(paymentEvent: PaymentEvent) {
        log.info("[Order]Consume Payment event {}", paymentEvent.paymentEventType)
        when (paymentEvent.paymentEventType) {
            PaymentEvent.PaymentEventType.CREATED -> onPaymentCreatedEvent(paymentEvent.payment)
            PaymentEvent.PaymentEventType.PURCHASED -> onPaymentPurchasedEvent(paymentEvent.payment)
        }
    }
}

