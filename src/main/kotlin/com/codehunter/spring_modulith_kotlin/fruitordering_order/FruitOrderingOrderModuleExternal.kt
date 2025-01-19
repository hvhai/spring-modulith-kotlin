package com.codehunter.spring_modulith_kotlin.fruitordering_order

import com.codehunter.spring_modulith_kotlin.AuthenticationUtil
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.ResponseDTO
import com.codehunter.spring_modulith_kotlin.share.ResponseFormatter
import com.codehunter.spring_modulith_kotlin.share.UserDTO
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

class CreateOrderRequestDTO(val products: List<ProductRequestDTO>) {
    class ProductRequestDTO(val id: String)
}

interface OrderService {
    fun createOrder(createOrderRequest: CreateOrderRequestDTO, user: UserDTO): OrderDTO

    fun allOrders(): List<OrderDTO>

    fun getOrder(id: String): OrderDTO
}

@RestController
@RequestMapping("/api/fruit-ordering")
class OrderController(private val orderService: OrderService) {
    val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/orders")
    fun getAllOrders(): ResponseEntity<ResponseDTO<List<OrderDTO>>> {
        log.info("GET getAllOrders")
        val allOrders: List<OrderDTO> = orderService.allOrders()
        return ResponseFormatter.handleList(allOrders)
    }

    @PostMapping("/orders")
    fun createOrder(@RequestBody orderDTO: CreateOrderRequestDTO): ResponseEntity<ResponseDTO<OrderDTO>> {
        log.info("POST createOrder")
        val user: UserDTO = AuthenticationUtil.user
        val order = orderService.createOrder(orderDTO, user)
        return ResponseFormatter.handleSingle(order, HttpHeaders(), HttpStatus.CREATED)
    }

    @GetMapping("/orders/{id}")
    fun getOrderInfo(@PathVariable id: String): ResponseEntity<ResponseDTO<OrderDTO>> {
        log.info("GET getOrderInfo")
        val order = orderService.getOrder(id)
        return ResponseFormatter.handleSingle(order, HttpHeaders(), HttpStatus.OK)
    }


}