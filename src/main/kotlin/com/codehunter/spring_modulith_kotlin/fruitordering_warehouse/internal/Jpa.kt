package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant


class ProductOutOfStockException(message: String?, val product: JpaWarehouseProduct) : Exception(message)

class JpaListener {
    val log = LoggerFactory.getLogger(this::class.java)

    @PostPersist
    private fun afterCreate(product: JpaWarehouseProduct) {
        log.info("[PostPersist] JpaWarehouseProduct create product {}", product)
    }

    @PostLoad
    private fun postLoad(product: JpaWarehouseProduct) {
        product.originalQuantity = product.quantity
    }

    @PreUpdate
    private fun preUpdate(product: JpaWarehouseProduct) {
        val oldQuantity = product.originalQuantity
        val newQuantity = product.quantity
        log.info(
            "[PreUpdate] JpaWarehouseProduct quantity changed for product {} from {} to {}",
            product.id, oldQuantity, newQuantity
        )
        // Update the originalQuantity for future updates if needed
        product.originalQuantity = newQuantity
    }
}

@Entity
@Table(name = "fruit_warehouse_product")
@EntityListeners(JpaListener::class)
data class JpaWarehouseProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String?,
    @Column(unique = true)
    val name: String,
    val quantity: Int,
    val price: BigDecimal
) {
    @Transient
    var originalQuantity: Int? = null

    override fun toString(): String {
        return "JpaWarehouseProduct{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                '}';
    }

    fun reserveForOrder(): JpaWarehouseProduct {
        if (this.quantity < 1) {
            throw ProductOutOfStockException("Not enough quantity to reserve " + this, this);
        }
        return JpaWarehouseProduct(id, name, quantity - 1, price);
    }
}

@Repository
interface WarehouseProductRepository : JpaRepository<JpaWarehouseProduct, String> {
    fun findByName(name: String): JpaWarehouseProduct?
}

@Entity
@Table(name = "fruit_warehouse_product_history")
data class JpaWarehouseProductHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "product_id")
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    val productId: String,
    val oldQuantity: Int,
    val newQuantity: Int,
    @CreationTimestamp
    val createAt:Instant
)