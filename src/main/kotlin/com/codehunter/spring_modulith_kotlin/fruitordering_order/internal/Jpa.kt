package com.codehunter.spring_modulith_kotlin.fruitordering_order.internal

import com.codehunter.spring_modulith_kotlin.share.OrderStatus
import jakarta.persistence.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "fruit_order_product")
class JpaOrderProduct(
    @Id
    var id: String,
    @Column(unique = true)
    var name: String,
    var price: BigDecimal,

    @ManyToMany(mappedBy = "products")
    var orders: MutableSet<JpaOrder> = mutableSetOf()
) {
    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }

    override fun toString(): String {
        return "JpaOrderProduct{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                '}'
    }

    @PostPersist
    fun postPersist() {
        log.info("Product create {}", this)
    }
}

@Entity
@Table(name = "fruit_order_payment")
class JpaOrderPayment(
    @Id
    val id: String? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    var order: JpaOrder? = null,

    val totalAmount: BigDecimal? = null,

    val purchaseAt: Instant? = null
) : AbstractAggregateRoot<JpaOrderPayment>() {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }

    @PostPersist
    fun postPersist() {
        log.info("[PostPersist] JpaOrderPayment persisted with id {}", this.id)
    }
}

@Entity
@Table(name = "fruit_order_order")
class JpaOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,

    @Enumerated(EnumType.STRING)
    val orderStatus: OrderStatus,

    val totalAmount: BigDecimal? = null,

    @OneToOne(mappedBy = "order", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var payment: JpaOrderPayment? = null,


    @ManyToMany(
        cascade = [CascadeType.PERSIST, CascadeType.MERGE
        ]
    )
    @JoinTable(
        name = "fruit_order_order_product",
        joinColumns = [JoinColumn(name = "order_id")],
        inverseJoinColumns = [JoinColumn(name = "product_id")]
    )
    var products: MutableSet<JpaOrderProduct> = mutableSetOf()
) : AbstractAggregateRoot<JpaOrder?>() {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java)
    }

    @PostPersist
    fun postPersist() {
        log.info("[PostPersist] Order persisted with id {}", this.id)
    }

    constructor(products: MutableSet<JpaOrderProduct>) : this(
        id = null,
        orderStatus = OrderStatus.IN_PRODUCT_PREPARE,
        totalAmount = null,
        payment = null,
        products
    ) {
        log.info("Create Order")
    }

    fun registerForPayment(): JpaOrder {
        log.info("Register payment for Order id={}", this.id)
        val totalAmount: BigDecimal = products
            .map(JpaOrderProduct::price)
            .reduce { sum, bigDecimal -> sum.add(bigDecimal) }

        return JpaOrder(
            id = this.id,
            orderStatus = OrderStatus.IN_PAYMENT_REQUESTED,
            totalAmount = totalAmount,
            payment = null,
            products = this.products
        )
    }

    fun waitingForPayment(payment: JpaOrderPayment): JpaOrder {
        log.info("Register payment success, update order id={}", this.id)
        return JpaOrder(
            id = this.id,
            orderStatus = OrderStatus.WAITING_FOR_PURCHASE,
            totalAmount = this.totalAmount,
            payment = this.payment,
            products = this.products
        )
    }

    fun finish(): JpaOrder {
        log.info("Purchase success, update order id={}", this.id)
        return JpaOrder(
            id = this.id,
            orderStatus = OrderStatus.DONE,
            totalAmount = this.totalAmount,
            payment = this.payment,
            products = this.products
        )
    }

    fun cancel(): JpaOrder {
        log.info("Cancel order id={}", this.id)
        return JpaOrder(
            id = this.id,
            orderStatus = OrderStatus.CANCELED,
            totalAmount = this.totalAmount,
            payment = this.payment,
            products = this.products
        )
    }
}

@Repository
interface OrderPaymentRepository : JpaRepository<JpaOrderPayment, String>

@Repository
interface OrderProductRepository : JpaRepository<JpaOrderProduct, String>

@Repository
interface OrderRepository : JpaRepository<JpaOrder, String>
