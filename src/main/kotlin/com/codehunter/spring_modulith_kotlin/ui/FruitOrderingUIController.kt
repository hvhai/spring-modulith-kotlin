package com.codehunter.spring_modulith_kotlin.ui

import com.codehunter.spring_modulith_kotlin.fruitordering_order.CreateOrderRequestDTO
import com.codehunter.spring_modulith_kotlin.fruitordering_order.OrderService
import com.codehunter.spring_modulith_kotlin.fruitordering_payment.PaymentService
import com.codehunter.spring_modulith_kotlin.fruitordering_warehouse.WarehouseService
import com.codehunter.spring_modulith_kotlin.share.IdNotFoundException
import com.codehunter.spring_modulith_kotlin.share.OrderDTO
import com.codehunter.spring_modulith_kotlin.share.OrderStatus
import com.codehunter.spring_modulith_kotlin.share.UserDTO
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * Server-rendered Fruit Ordering pages. Calls the public services of the fruit-ordering
 * modules directly instead of the JWT-protected `/api` endpoints, authenticated with HTTP Basic.
 */
@Controller
@RequestMapping("/fruit-ordering")
class FruitOrderingUIController(
    private val warehouseService: WarehouseService,
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {
    val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val INDEX_REDIRECT = "redirect:/fruit-ordering"

        // Statuses still moving through the async Order -> Warehouse -> Payment event flow
        private val IN_PROGRESS_STATUSES = setOf(
            OrderStatus.PENDING,
            OrderStatus.IN_PRODUCT_PREPARE,
            OrderStatus.IN_PAYMENT_REQUESTED,
            OrderStatus.CANCELING,
        )
    }

    /** The navbar reads `profile` entries on every page when the user is authenticated. */
    @ModelAttribute
    fun addProfile(model: Model, authentication: Authentication?) {
        if (authentication != null) {
            model.addAttribute("profile", mapOf("name" to authentication.name, "picture" to ""))
        }
    }

    @GetMapping
    fun index(model: Model): String {
        model.addAttribute("products", warehouseService.allProduct())
        model.addAttribute("orders", orderService.allOrders())
        return "fruit-ordering/index"
    }

    @PostMapping("/orders")
    fun createOrder(
        @RequestParam(required = false) productIds: List<String>?,
        authentication: Authentication,
        redirectAttributes: RedirectAttributes,
    ): String {
        val selectedIds = productIds.orEmpty().toSet()
        if (selectedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one product")
            return INDEX_REDIRECT
        }
        // Order module silently drops unknown ids, and an order without products breaks payment registration
        val knownIds = warehouseService.allProduct().map { it.id }.toSet()
        if (!knownIds.containsAll(selectedIds)) {
            redirectAttributes.addFlashAttribute("error", "Unknown product selected")
            return INDEX_REDIRECT
        }

        val request = CreateOrderRequestDTO(selectedIds.map { CreateOrderRequestDTO.ProductRequestDTO(it) })
        val order = orderService.createOrder(request, userFrom(authentication))
        log.info("UI created order id={}", order.id)
        return "redirect:/fruit-ordering/orders/${order.id}"
    }

    @GetMapping("/orders/{id}")
    fun orderDetail(
        @PathVariable id: String,
        @RequestParam(defaultValue = "false") purchasing: Boolean,
        model: Model,
        redirectAttributes: RedirectAttributes,
    ): String {
        val order = try {
            orderService.getOrder(id)
        } catch (e: IdNotFoundException) {
            redirectAttributes.addFlashAttribute("error", "Order not found")
            return INDEX_REDIRECT
        }
        val isPurchasing = purchasing && order.orderStatus == OrderStatus.WAITING_FOR_PURCHASE
        model.addAttribute("order", order)
        model.addAttribute("purchasing", isPurchasing)
        model.addAttribute("autoRefresh", shouldAutoRefresh(order, isPurchasing))
        return "fruit-ordering/order-detail"
    }

    @PostMapping("/orders/{id}/purchase")
    fun purchase(@PathVariable id: String, redirectAttributes: RedirectAttributes): String {
        val order = try {
            orderService.getOrder(id)
        } catch (e: IdNotFoundException) {
            redirectAttributes.addFlashAttribute("error", "Order not found")
            return INDEX_REDIRECT
        }
        // Payment id is derived server-side so a user can only purchase the payment of this order
        val payment = order.payment
        if (order.orderStatus != OrderStatus.WAITING_FOR_PURCHASE || payment == null) {
            redirectAttributes.addFlashAttribute("error", "Order is not waiting for purchase")
            return "redirect:/fruit-ordering/orders/$id"
        }
        try {
            paymentService.purchasePayment(payment.id)
        } catch (e: IllegalArgumentException) {
            // Repeat submit before the async PURCHASED event moves the order to DONE
            redirectAttributes.addFlashAttribute("error", "Payment already purchased")
            return "redirect:/fruit-ordering/orders/$id?purchasing=true"
        }
        log.info("UI purchased payment id={} for order id={}", payment.id, id)
        return "redirect:/fruit-ordering/orders/$id?purchasing=true"
    }

    private fun userFrom(authentication: Authentication) =
        UserDTO(authentication.name, authentication.name)

    /** After purchase the status stays WAITING_FOR_PURCHASE until the PURCHASED event is consumed. */
    private fun shouldAutoRefresh(order: OrderDTO, purchasing: Boolean) =
        order.orderStatus in IN_PROGRESS_STATUSES || purchasing
}
