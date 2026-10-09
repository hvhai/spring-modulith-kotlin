package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.Instant


class ProductOutOfStockException(message: String?, val product: JpaWarehouseProduct) : Exception(message)

class JpaListener {
    val log = LoggerFactory.getLogger(this::class.java)
    @PostPersist
    private fun afterCreate(product: QuantityAuditableEntity) {
        log.info("[PostPersist] JpaWarehouseProduct create product {}", product)
    }

    @PostLoad
    private fun postLoad(product: QuantityAuditableEntity) {
        product.originalQuantity = product.quantity
    }

    @PreUpdate
    private fun preUpdate(product: QuantityAuditableEntity) {
        val oldQuantity = product.originalQuantity
        val newQuantity = product.quantity
        log.info(
            "[PreUpdate] JpaWarehouseProduct quantity changed for product {} from {} to {}",
            product.id, oldQuantity, newQuantity
        )
    }

    @PostUpdate
    private fun postUpdate(product: QuantityAuditableEntity) {
        val oldQuantity = product.originalQuantity
        val newQuantity = product.quantity
        if (oldQuantity != null && oldQuantity != newQuantity && product is JpaWarehouseProduct) {
            // Create a new history record
            val history = JpaWarehouseProductHistory(
                id = 0, // Will be auto-generated
                product = product,
                oldQuantity = oldQuantity,
                newQuantity = newQuantity,
            )
            // Add to the product's history collection
//            product.addProductHistory(history)
            // Persist the history entity manually
            // Since we don't have direct access to EntityManager here, use a workaround:
            JpaListenerHelper.persistHistory(history)
            log.info(
                "[PostUpdate] JpaWarehouseProductHistory created for product {}: {} -> {}",
                product.id, oldQuantity, newQuantity
            )
            // Update the originalQuantity for future updates if needed
            product.originalQuantity = newQuantity
        }
    }
}

// Helper object to persist history entity since EntityListeners can't inject dependencies
object JpaListenerHelper {
    @Volatile
    private var historyRepository: WarehouseProductHistoryRepository? = null

    fun setHistoryRepository(repo: WarehouseProductHistoryRepository) {
        historyRepository = repo
    }

    fun persistHistory(history: JpaWarehouseProductHistory) {
        historyRepository?.save(history)
    }
}

@MappedSuperclass
@EntityListeners(JpaListener::class)
abstract class QuantityAuditableEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: String?,
    val quantity: Int,
) {
    @OneToMany(
        mappedBy = "product",
        fetch = FetchType.LAZY,
//        orphanRemoval = true,
        cascade = [CascadeType.ALL]
    )
    private val productHistories = mutableListOf<JpaWarehouseProductHistory>()

    @Transient
    var originalQuantity: Int? = null

    fun addProductHistory(productHistory: JpaWarehouseProductHistory) {
        productHistories.add(productHistory)
    }
}

@Entity
@Table(name = "fruit_warehouse_product")
data class JpaWarehouseProduct(
    override val id: String? = null,
    @Column(unique = true)
    val name: String,
    override val quantity: Int,
    val price: BigDecimal,
) : QuantityAuditableEntity(id, quantity){


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

@Repository
interface WarehouseProductHistoryRepository : JpaRepository<JpaWarehouseProductHistory, Long>

@Entity
@Table(name = "fruit_warehouse_product_history")
@EntityListeners(AuditingEntityListener::class)
data class JpaWarehouseProductHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    val product: JpaWarehouseProduct,
    @Column(name = "created_by", nullable = false)
    @CreatedBy
    var createdBy: String? = null,
    @Column(name = "old_quantity")
    val oldQuantity: Int,
    @Column(name = "new_quantity", nullable = false)
    val newQuantity: Int,
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    var createdAt: Instant? = null
)

@Component
class EntityListenerInitializer(
    private val historyRepository: WarehouseProductHistoryRepository
) {
    init {
        JpaListenerHelper.setHistoryRepository(historyRepository)
    }
}