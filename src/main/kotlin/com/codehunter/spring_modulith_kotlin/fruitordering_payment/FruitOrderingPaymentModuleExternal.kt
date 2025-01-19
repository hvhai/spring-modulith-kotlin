package com.codehunter.spring_modulith_kotlin.fruitordering_payment

import com.codehunter.spring_modulith_kotlin.AuthenticationUtil
import com.codehunter.spring_modulith_kotlin.share.*
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

interface PaymentService {
    fun purchasePayment(id: String): PaymentDTO

    fun getPayment(id: String): PaymentDTO

    fun allPayments(): List<PaymentDTO>

    fun createPayment(request: OrderDTO)
}

@RestController
@RequestMapping("/api/fruit-ordering")
class PaymentController(private val paymentService: PaymentService) {
    val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/payments")
    fun getAllPayments(): ResponseEntity<ResponseDTO<List<PaymentDTO>>> {
        log.info("GET getAllPayments")
        val allPayments: List<PaymentDTO> = paymentService.allPayments()
        return ResponseFormatter.handleList(allPayments)
    }

    @GetMapping("/payments/{id}")
    fun getPaymentInfo(@PathVariable id: String): ResponseEntity<ResponseDTO<PaymentDTO>> {
        log.info("GET getPaymentInfo")
        val payment = paymentService.getPayment(id)
        return ResponseFormatter.handleSingle(payment, HttpHeaders(), HttpStatus.OK)
    }

    @PostMapping("/payments/{id}/purchase")
    fun purchaseAPayment(@PathVariable id: String): ResponseEntity<ResponseDTO<PaymentDTO>> {
        val user: UserDTO = AuthenticationUtil.user
        log.info("POST user: {} purchaseAPayment", user.id)
        val payment = paymentService.purchasePayment(id)
        return ResponseFormatter.handleSingle(payment, HttpHeaders(), HttpStatus.CREATED)
    }

}