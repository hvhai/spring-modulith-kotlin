package com.codehunter.spring_modulith_kotlin.share

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.Instant

data class ProductDTO(val id: String, val name: String, val price: BigDecimal)

data class PaymentDTO(val id: String, val orderId: String, val totalAmount: BigDecimal, val purchaseAt: Instant?)

data class OrderDTO(
    @JsonInclude(JsonInclude.Include.NON_NULL) val id: String,
    @JsonInclude(JsonInclude.Include.NON_NULL) val orderStatus: OrderStatus,
    val totalAmount: BigDecimal,
    val payment: PaymentDTO,
    val products: Set<ProductDTO>
)

enum class OrderStatus {
    PENDING,
    IN_PRODUCT_PREPARE,
    IN_PAYMENT_REQUESTED,
    WAITING_FOR_PURCHASE,
    DONE,
    CANCELING,
    CANCELED
}
