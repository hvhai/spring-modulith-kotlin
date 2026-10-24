package com.codehunter.spring_modulith_kotlin.ui

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseService
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseProductDTO
import com.codehunter.spring_modulith_kotlin.fruitordering_order.OrderService
import com.codehunter.spring_modulith_kotlin.fruitordering_payment.PaymentService
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.ProductDTO
import com.codehunter.spring_modulith_kotlin.share.UserDTO
import com.codehunter.spring_modulith_kotlin.share.OrderStatus
import com.codehunter.spring_modulith_kotlin.share.PaymentDTO
import com.codehunter.spring_modulith_kotlin.share.IdNotFoundException
import com.codehunter.spring_modulith_kotlin.fruitordering_order.CreateOrderRequestDTO
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(
    FruitOrderingUIController::class,
    properties = [
        "fruit-ordering.basic-auth.username=alice",
        "fruit-ordering.basic-auth.password=secret",
    ],
)
@Import(FruitOrderingBasicAuthConfiguration::class)
class FruitOrderingUIControllerTest(
    @Autowired val mockMvc: MockMvc
) {
    @MockkBean
    lateinit var warehouseService: WarehouseService

    @MockkBean
    lateinit var orderService: OrderService

    @MockkBean
    lateinit var paymentService: PaymentService

    private fun basicAlice() = httpBasic("alice", "secret")

    // Case 1: GET /fruit-ordering -> 200, view fruit-ordering/index, contains product names + order id; navbar contains "Fruit Ordering"
    @Test
    fun `case1_get_index_returns_200_with_products_and_orders`() {
        // given
        val warehouseProduct = WarehouseProductDTO("p1", "Apple", 10, BigDecimal("1.50"))
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.PENDING,
            totalAmount = BigDecimal("1.50"),
            payment = null,
            products = setOf(product)
        )
        every { warehouseService.allProduct() }.returns(listOf(warehouseProduct))
        every { orderService.allOrders() }.returns(listOf(order))

        // when & then
        mockMvc.get("/fruit-ordering") {
            with(basicAlice())
        }
            .andExpect { status { isOk() } }
            .andExpect { view { name("fruit-ordering/index") } }
            .andExpect { model { attributeExists("products") } }
            .andExpect { model { attributeExists("orders") } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Apple")) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("order-1")) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Fruit Ordering")) } }
    }

    // Case 2: POST orders with no productIds -> 3xx to /fruit-ordering, flash error, createOrder never called
    @Test
    fun `case2_post_order_with_no_products_redirects_with_error`() {
        // when & then
        mockMvc.post("/fruit-ordering/orders") {
            with(basicAlice())
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering") }
            .andExpect { flash { attribute("error", org.hamcrest.Matchers.containsString("Select at least one product")) } }

        // verify createOrder was never called
        verify(exactly = 0) { orderService.createOrder(any(), any()) }
    }

    // Case 3: POST orders with unknown product id -> 3xx to /fruit-ordering, flash error, createOrder never called
    @Test
    fun `case3_post_order_with_unknown_product_id_redirects_with_error`() {
        // given
        val knownProduct = WarehouseProductDTO("p1", "Apple", 10, BigDecimal("1.50"))
        every { warehouseService.allProduct() }.returns(listOf(knownProduct))

        // when & then
        mockMvc.post("/fruit-ordering/orders") {
            with(basicAlice())
            param("productIds", "unknown-id")
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering") }
            .andExpect { flash { attribute("error", org.hamcrest.Matchers.containsString("Unknown product")) } }

        // verify createOrder was never called
        verify(exactly = 0) { orderService.createOrder(any(), any()) }
    }

    // Case 4: POST orders valid -> createOrder called with ids and UserDTO("alice","alice"), 3xx to /fruit-ordering/orders/{id}
    @Test
    fun `case4_post_order_with_valid_products_creates_order_and_redirects`() {
        // given
        val productId = "p1"
        val warehouseProduct = WarehouseProductDTO(productId, "Apple", 10, BigDecimal("1.50"))
        val product = ProductDTO(productId, "Apple", BigDecimal("1.50"))
        val createdOrder = OrderDTO(
            id = "order-123",
            orderStatus = OrderStatus.PENDING,
            totalAmount = BigDecimal("1.50"),
            payment = null,
            products = setOf(product)
        )
        every { warehouseService.allProduct() }.returns(listOf(warehouseProduct))
        every { orderService.createOrder(any(), any()) }.returns(createdOrder)

        // when & then
        mockMvc.post("/fruit-ordering/orders") {
            with(basicAlice())
            param("productIds", productId)
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering/orders/order-123") }

        // verify createOrder was called with correct arguments
        verify(exactly = 1) {
            orderService.createOrder(
                match { req -> req.products.size == 1 && req.products[0].id == productId },
                UserDTO("alice", "alice")
            )
        }
    }

    // Case 5: GET detail IN_PRODUCT_PREPARE -> reload script present, no purchase form
    @Test
    fun `case5_get_detail_in_product_prepare_shows_reload_no_purchase_form`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.IN_PRODUCT_PREPARE,
            totalAmount = BigDecimal("1.50"),
            payment = null,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.get("/fruit-ordering/orders/order-1") {
            with(basicAlice())
        }
            .andExpect { status { isOk() } }
            .andExpect { view { name("fruit-ordering/order-detail") } }
            .andExpect { model { attribute("autoRefresh", true) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("id=\"autoRefresh\"")) } }
            .andExpect { content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"purchaseForm\"")) ) } }
    }

    // Case 6: GET detail WAITING_FOR_PURCHASE + payment -> purchase form present, no reload script
    @Test
    fun `case6_get_detail_waiting_for_purchase_with_payment_shows_purchase_form_no_reload`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val payment = PaymentDTO("pay-1", "order-1", BigDecimal("1.50"), null)
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.WAITING_FOR_PURCHASE,
            totalAmount = BigDecimal("1.50"),
            payment = payment,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.get("/fruit-ordering/orders/order-1") {
            with(basicAlice())
        }
            .andExpect { status { isOk() } }
            .andExpect { view { name("fruit-ordering/order-detail") } }
            .andExpect { model { attribute("autoRefresh", false) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("id=\"purchaseForm\"")) } }
            .andExpect { content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"autoRefresh\"")) ) } }
    }

    // Case 7: GET detail WAITING_FOR_PURCHASE + purchasing=true -> reload, no purchase form
    @Test
    fun `case7_get_detail_waiting_for_purchase_with_purchasing_true_shows_reload_no_form`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val payment = PaymentDTO("pay-1", "order-1", BigDecimal("1.50"), Instant.now())
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.WAITING_FOR_PURCHASE,
            totalAmount = BigDecimal("1.50"),
            payment = payment,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.get("/fruit-ordering/orders/order-1?purchasing=true") {
            with(basicAlice())
        }
            .andExpect { status { isOk() } }
            .andExpect { view { name("fruit-ordering/order-detail") } }
            .andExpect { model { attribute("autoRefresh", true) } }
            .andExpect { model { attribute("purchasing", true) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("id=\"autoRefresh\"")) } }
            .andExpect { content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"purchaseForm\"")) ) } }
    }

    // Case 8: GET detail DONE/CANCELED -> no reload; CANCELED alert
    @Test
    fun `case8_get_detail_done_no_reload`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.DONE,
            totalAmount = BigDecimal("1.50"),
            payment = null,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.get("/fruit-ordering/orders/order-1") {
            with(basicAlice())
        }
            .andExpect { status { isOk() } }
            .andExpect { view { name("fruit-ordering/order-detail") } }
            .andExpect { model { attribute("autoRefresh", false) } }
            .andExpect { content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"autoRefresh\"")) ) } }
    }

    @Test
    fun `case8_get_detail_canceled_shows_alert`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.CANCELED,
            totalAmount = BigDecimal("1.50"),
            payment = null,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.get("/fruit-ordering/orders/order-1") {
            with(basicAlice())
        }
            .andExpect { status { isOk() } }
            .andExpect { view { name("fruit-ordering/order-detail") } }
            .andExpect { model { attribute("autoRefresh", false) } }
            .andExpect { content { string(org.hamcrest.Matchers.containsString("Order canceled")) } }
    }

    // Case 9: POST purchase at WAITING_FOR_PURCHASE -> purchasePayment called, redirect ?purchasing=true
    @Test
    fun `case9_post_purchase_at_waiting_for_purchase_calls_purchase_and_redirects`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val payment = PaymentDTO("pay-1", "order-1", BigDecimal("1.50"), null)
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.WAITING_FOR_PURCHASE,
            totalAmount = BigDecimal("1.50"),
            payment = payment,
            products = setOf(product)
        )
        val purchasedPayment = PaymentDTO("pay-1", "order-1", BigDecimal("1.50"), Instant.now())
        every { orderService.getOrder("order-1") }.returns(order)
        every { paymentService.purchasePayment("pay-1") }.returns(purchasedPayment)

        // when & then
        mockMvc.post("/fruit-ordering/orders/order-1/purchase") {
            with(basicAlice())
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering/orders/order-1?purchasing=true") }

        // verify purchasePayment was called
        verify(exactly = 1) { paymentService.purchasePayment("pay-1") }
    }

    // Case 10: POST purchase at other status -> not called, flash error
    @Test
    fun `case10_post_purchase_at_pending_status_does_not_call_purchase`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.PENDING,
            totalAmount = BigDecimal("1.50"),
            payment = null,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.post("/fruit-ordering/orders/order-1/purchase") {
            with(basicAlice())
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering/orders/order-1") }
            .andExpect { flash { attribute("error", org.hamcrest.Matchers.containsString("not waiting for purchase")) } }

        // verify purchasePayment was never called
        verify(exactly = 0) { paymentService.purchasePayment(any()) }
    }

    @Test
    fun `case10_post_purchase_without_payment_does_not_call_purchase`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.WAITING_FOR_PURCHASE,
            totalAmount = BigDecimal("1.50"),
            payment = null,  // No payment
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)

        // when & then
        mockMvc.post("/fruit-ordering/orders/order-1/purchase") {
            with(basicAlice())
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering/orders/order-1") }
            .andExpect { flash { attribute("error", org.hamcrest.Matchers.containsString("not waiting for purchase")) } }

        // verify purchasePayment was never called
        verify(exactly = 0) { paymentService.purchasePayment(any()) }
    }

    // Case 11: GET detail unknown id (IdNotFoundException) -> redirect /fruit-ordering
    @Test
    fun `case11_get_detail_unknown_id_redirects_to_index`() {
        // given
        every { orderService.getOrder("unknown-id") }.throws(IdNotFoundException("Order not found"))

        // when & then
        mockMvc.get("/fruit-ordering/orders/unknown-id") {
            with(basicAlice())
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering") }
            .andExpect { flash { attribute("error", org.hamcrest.Matchers.containsString("Order not found")) } }
    }

    // Case 12: browser-like unauthenticated request -> 401 Basic challenge (no Auth0 redirect)
    @Test
    fun `case12_get_index_unauthenticated_returns_basic_challenge`() {
        mockMvc.get("/fruit-ordering") {
            accept = org.springframework.http.MediaType.TEXT_HTML
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { header { string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")) } }
    }

    // Case 13: POST purchase at WAITING_FOR_PURCHASE when payment already purchased (repeated submit)
    // -> purchasePayment throws IllegalArgumentException -> redirect ?purchasing=true with error flash
    @Test
    fun `case13_post_purchase_when_payment_already_purchased_shows_error_and_redirects`() {
        // given
        val product = ProductDTO("p1", "Apple", BigDecimal("1.50"))
        val payment = PaymentDTO("pay-1", "order-1", BigDecimal("1.50"), null)
        val order = OrderDTO(
            id = "order-1",
            orderStatus = OrderStatus.WAITING_FOR_PURCHASE,
            totalAmount = BigDecimal("1.50"),
            payment = payment,
            products = setOf(product)
        )
        every { orderService.getOrder("order-1") }.returns(order)
        every { paymentService.purchasePayment("pay-1") }.throws(IllegalArgumentException("Payment already purchased"))

        // when & then
        mockMvc.post("/fruit-ordering/orders/order-1/purchase") {
            with(basicAlice())
        }
            .andExpect { status { is3xxRedirection() } }
            .andExpect { redirectedUrl("/fruit-ordering/orders/order-1?purchasing=true") }
            .andExpect { flash { attribute("error", org.hamcrest.Matchers.containsString("Payment already purchased")) } }

        // verify purchasePayment was called
        verify(exactly = 1) { paymentService.purchasePayment("pay-1") }
    }

    // Case: no credentials -> 401 with Basic challenge, service never touched
    @Test
    fun `get_index_without_credentials_returns_401`() {
        mockMvc.get("/fruit-ordering")
            .andExpect { status { isUnauthorized() } }
            .andExpect { header { string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")) } }
        verify(exactly = 0) { warehouseService.allProduct() }
    }

    // Case: wrong password -> 401
    @Test
    fun `wrong_password_returns_401`() {
        mockMvc.get("/fruit-ordering") {
            with(httpBasic("alice", "wrong"))
        }
            .andExpect { status { isUnauthorized() } }
        verify(exactly = 0) { warehouseService.allProduct() }
    }
}
