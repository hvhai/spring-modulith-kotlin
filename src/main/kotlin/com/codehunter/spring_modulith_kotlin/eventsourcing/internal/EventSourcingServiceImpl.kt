package com.codehunter.spring_modulith_kotlin.eventsourcing.internal

import com.codehunter.spring_modulith_kotlin.eventsourcing.*
import io.opentelemetry.api.trace.Span
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class EventSourcingServiceImpl(private val applicationEventPublisher: ApplicationEventPublisher) : EventSourcingService {
    override fun addOrderEvent(event: OrderEvent) {
        val span: Span = Span.current()
        span.setAttribute("event.publish", event.orderEventType.toString())
        applicationEventPublisher.publishEvent(event)
    }

    override fun addPaymentEvent(event: PaymentEvent) {
        val span: Span = Span.current()
        span.setAttribute("event.publish", event.paymentEventType.toString())
        applicationEventPublisher.publishEvent(event)
    }

    override fun addWarehouseEvent(event: WarehouseEvent) {
        val span: Span = Span.current()
        span.setAttribute("event.publish", event.warehouseEventType.toString())
        applicationEventPublisher.publishEvent(event)
    }

    override fun addNotificationEvent(event: NotificationEvent) {
        val span: Span = Span.current()
        span.setAttribute("event.publish", event.notificationEventType.toString())
        applicationEventPublisher.publishEvent(event)
    }

}