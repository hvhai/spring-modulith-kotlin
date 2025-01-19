package com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.internal

import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseProductDTO
import com.codehunter.spring_modulith_kotlin.share.ProductDTO

fun JpaWarehouseProduct.toDTO(): WarehouseProductDTO {
    return WarehouseProductDTO(id!!, name, quantity, price)
}

fun JpaWarehouseProduct.toProductDTO(): ProductDTO {
    return ProductDTO(id!!, name, price)
}
