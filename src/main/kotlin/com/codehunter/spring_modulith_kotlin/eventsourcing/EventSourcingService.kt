package com.codehunter.spring_modulith_kotlin.eventsourcing

interface EventSourcingService {
    fun addOrderEvent(event: OrderEvent)

    fun addPaymentEvent(event: PaymentEvent)

    fun addWarehouseEvent(event: WarehouseEvent)

    fun addNotificationEvent(event: NotificationEvent)
}