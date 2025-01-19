package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse

import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.ProductDTO
import com.codehunter.spring_modulith_kotlin.share.ResponseDTO
import com.codehunter.spring_modulith_kotlin.share.ResponseFormatter
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class WarehouseProductDTO(val id: String, val name: String, val quantity: Int, val price: BigDecimal)

interface WarehouseService {
    fun reserveProductForOrder(request: OrderDTO)

    fun allProduct(): List<WarehouseProductDTO>

    fun getProduct(id: String): ProductDTO
}

@RestController
@RequestMapping("/api/fruit-ordering")
class WarehouseController(private val warehouseService: WarehouseService) {
    val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/products")
    fun getAllProduct(): ResponseEntity<ResponseDTO<List<WarehouseProductDTO>>> {
        log.info("GET getAllProduct")
        val allProduct: List<WarehouseProductDTO> = warehouseService.allProduct()
        return ResponseFormatter.handleList(allProduct)
    }

    @GetMapping("/products/{id}")
    fun getProductInfo(@PathVariable id: String): ResponseEntity<ResponseDTO<ProductDTO>> {
        log.info("GET getProductInfo")
        val product = warehouseService.getProduct(id)
        return ResponseFormatter.handleSingle(product, HttpHeaders(), HttpStatus.OK)
    }

}