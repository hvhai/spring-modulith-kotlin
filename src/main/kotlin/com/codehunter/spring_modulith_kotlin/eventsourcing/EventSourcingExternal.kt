package com.codehunter.spring_modulith_kotlin.eventsourcing

import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.PaymentDTO
import com.codehunter.spring_modulith_kotlin.share.ProductDTO

data class NotificationEvent(
    val orderId: String,
    val notificationEventType: NotificationEventType,
    val message: String
) {
    enum class NotificationEventType {
        ORDER_CREATED,
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        ORDER_COMPLETED,
        ORDER_CANCELLED,
    }
}

data class WarehouseEvent(
    val products: List<ProductDTO>,
    val orderId: String,
    val warehouseEventType: WarehouseEventType
) {
    enum class WarehouseEventType {
        ADDED,
        RESERVE_COMPLETED,
        OUT_OF_STOCK,
    }
}

data class PaymentEvent(val payment: PaymentDTO, val paymentEventType: PaymentEventType) {
    enum class PaymentEventType {
        CREATED,
        PURCHASED,
    }
}

data class OrderEvent(val order: OrderDTO, val orderEventType: OrderEventType) {
    enum class OrderEventType {
        CREATED,
        PAYMENT_REQUESTED,
        CANCELLED
    }
}
