package com.codehunter.spring_modulith_kotlin.fruitordering_payment.internal

import com.codehunter.spring_modulith_kotlin.eventsourcing.EventSourcingService
import com.codehunter.spring_modulith_kotlin.eventsourcing.OrderEvent
import com.codehunter.spring_modulith_kotlin.eventsourcing.PaymentEvent
import com.codehunter.spring_modulith_kotlin.fruitordering_payment.PaymentService
import com.codehunter.spring_modulith_kotlin.share.IdNotFoundException
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.PaymentDTO
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val eventSourcingService: EventSourcingService
) : PaymentService {
    val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    override fun purchasePayment(id: String): PaymentDTO {
        val paymentOptional = paymentRepository.findById(id)
        if (paymentOptional.isEmpty) {
            throw IdNotFoundException(String.format("Payment not found, id=%s", id))
        }
        val payment = paymentOptional.get()
        val updatedPayment = paymentRepository.save(payment.purchase())
        val paymentDTO: PaymentDTO = updatedPayment.toDTO()
        log.info("[PaymentPurchasedEvent]Payment is purchased for OrderId={}", payment.orderId)
        //        applicationEventPublisher.publishEvent(new PaymentPurchasedEvent(paymentDTO));
        eventSourcingService.addPaymentEvent(PaymentEvent(paymentDTO, PaymentEvent.PaymentEventType.PURCHASED))
        return paymentDTO
    }

    override fun getPayment(id: String): PaymentDTO {
        val paymentOptional = paymentRepository.findById(id)
        if (paymentOptional.isEmpty) {
            throw IdNotFoundException(String.format("Payment not found, id=%s", id))
        }
        return paymentOptional.get().toDTO()
    }

    override fun allPayments(): List<PaymentDTO> {
        val allOrders = paymentRepository.findAll()
        return allOrders.map { it.toDTO() }
    }

    @Transactional
    override fun createPayment(request: OrderDTO) {
        val newPayment = JpaPayment(null, orderId = request.id, request.totalAmount)
        val createdPayment = paymentRepository.save(newPayment)
        val paymentDTO: PaymentDTO = createdPayment.toDTO()
        log.info("[PaymentCreatedEvent]Payment created for OrderId={}", createdPayment.orderId)
        //        applicationEventPublisher.publishEvent(new PaymentCreatedEvent(paymentDTO));
        eventSourcingService.addPaymentEvent(PaymentEvent(paymentDTO, PaymentEvent.PaymentEventType.CREATED))
    }
}

@Component
class PaymentEventHandler(private val paymentService: PaymentService) {
    val log = LoggerFactory.getLogger(this::class.java)

    @ApplicationModuleListener
    fun onOrderEvent(orderEvent: OrderEvent) {
        log.info("[Payment]Consume Order event {}", orderEvent.orderEventType)
        when (orderEvent.orderEventType) {
            OrderEvent.OrderEventType.CREATED, OrderEvent.OrderEventType.CANCELLED -> log.info("Do nothing")
            OrderEvent.OrderEventType.PAYMENT_REQUESTED -> paymentService.createPayment(orderEvent.order)
        }
    }
}