package com.codehunter.spring_modulith_kotlin.fruitordering_order.internal

import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.PaymentDTO
import com.codehunter.spring_modulith_kotlin.share.ProductDTO
import java.math.BigDecimal

fun JpaOrderPayment.toDTO() = PaymentDTO(
    id = this.id!!,
    orderId = this.order?.id!!,
    totalAmount = this.totalAmount ?: BigDecimal.ZERO,
    purchaseAt = this.purchaseAt
)

fun JpaOrderProduct.toDTO() = ProductDTO(
    id = this.id,
    name = this.name,
    price = this.price
)

fun JpaOrder.toDTO() = OrderDTO(
    id = this.id!!,
    totalAmount = this.totalAmount,
    orderStatus = this.orderStatus,
    payment = this.payment?.toDTO(),
    products = this.products.map { it.toDTO() }.toSet()
)