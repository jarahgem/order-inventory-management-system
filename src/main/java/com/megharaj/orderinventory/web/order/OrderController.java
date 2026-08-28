package com.megharaj.orderinventory.web.order;

import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.payment.application.PaymentApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP adapter for order creation, lookup, payment simulation, and cancellation.
 *
 * <p>Business state transitions remain in the application and domain layers. This class
 * translates validated HTTP input into application-service calls and exposes their views.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final PaymentApplicationService paymentApplicationService;

    /**
     * Creates the order HTTP adapter.
     *
     * @param orderApplicationService order creation, lookup, and cancellation use cases
     * @param paymentApplicationService idempotent payment orchestration use case
     */
    public OrderController(
            OrderApplicationService orderApplicationService,
            PaymentApplicationService paymentApplicationService) {
        this.orderApplicationService = orderApplicationService;
        this.paymentApplicationService = paymentApplicationService;
    }

    /**
     * Creates an order and atomically reserves every requested product.
     *
     * <p>The order is persisted only when all inventory updates succeed.
     *
     * @param request validated customer reference and order lines
     * @return a {@code 201 Created} response with status {@code INVENTORY_RESERVED} and a
     *     {@code Location} header for the new order
     */
    @PostMapping
    public ResponseEntity<OrderView> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderView created = orderApplicationService.createAndReserve(request.toCommand());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(created.orderId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Retrieves an order with its immutable item snapshots and current status.
     *
     * @param orderId order identifier from the request path
     * @return current order representation
     */
    @GetMapping("/{orderId}")
    public OrderView getOrder(@PathVariable UUID orderId) {
        return orderApplicationService.getOrder(orderId);
    }

    /**
     * Simulates an idempotent payment and confirms or fails the order.
     *
     * <p>Retries for the same order must reuse both the idempotency key and requested
     * outcome. A concurrent retry can observe {@code PAYMENT_PENDING} while another request
     * owns the processing lease.
     *
     * @param orderId order whose reserved inventory will be committed or released
     * @param idempotencyKey unique 8-128 character key supplied in the request header
     * @param request requested simulated gateway outcome
     * @return the current order, normally {@code CONFIRMED} or {@code PAYMENT_FAILED}
     */
    @PostMapping("/{orderId}/payments")
    public OrderView simulatePayment(
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SimulatePaymentRequest request) {
        return paymentApplicationService.processPayment(
                orderId, idempotencyKey, request.outcome());
    }

    /**
     * Cancels an eligible order and releases any reserved inventory.
     *
     * <p>Repeating a cancellation for an already cancelled or payment-failed order is
     * idempotent. Confirmed and payment-pending orders are rejected.
     *
     * @param orderId order identifier from the request path
     * @return the cancelled order or its existing terminal failed/cancelled state
     */
    @PostMapping("/{orderId}/cancellations")
    public OrderView cancelOrder(@PathVariable UUID orderId) {
        return orderApplicationService.cancelOrder(orderId);
    }
}
