package com.codehunter.spring_modulith_kotlin.fruitordering_payment.internal

import com.codehunter.spring_modulith_kotlin.share.PaymentDTO

fun JpaPayment.toDTO() = PaymentDTO(
    id = this.id!!,
    orderId = this.orderId,
    totalAmount = this.totalAmount,
    purchaseAt = this.purchaseAt
)