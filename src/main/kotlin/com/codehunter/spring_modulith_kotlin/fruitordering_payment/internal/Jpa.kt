package com.codehunter.spring_modulith_kotlin.fruitordering_payment.internal

import jakarta.persistence.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant


@Entity
@Table(name = "fruit_payment_payment")
class JpaPayment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    val orderId: String,
    val totalAmount: BigDecimal?,
    val purchaseAt: Instant? = null

) : AbstractAggregateRoot<JpaPayment>() {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }

    @PostPersist
    fun postPersist() {
        log.info("[PostPersist] JpaPayment persisted with id {}", this.id)
    }

    fun purchase(): JpaPayment {
        log.info("Purchase payment id={}, orderId={}", this.id, this.orderId)
        return JpaPayment(id, orderId, totalAmount, Instant.now())
    }
}

@Repository
interface PaymentRepository : JpaRepository<JpaPayment, String> {
    fun findByOrderId(orderId: String): List<JpaPayment>
}
