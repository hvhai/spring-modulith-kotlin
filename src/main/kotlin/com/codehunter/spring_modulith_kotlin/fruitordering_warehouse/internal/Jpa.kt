package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal

import jakarta.persistence.*
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal

class ProductOutOfStockException(message: String?, val product: JpaWarehouseProduct) : Exception(message)

class JpaListener {
    val log = LoggerFactory.getLogger(this::class.java)

    @PostPersist
    private fun afterCreate(product: JpaWarehouseProduct) {
        log.info("[PostPersist] JpaWarehouseProduct create product {}", product)
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