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

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final PaymentApplicationService paymentApplicationService;

    public OrderController(
            OrderApplicationService orderApplicationService,
            PaymentApplicationService paymentApplicationService) {
        this.orderApplicationService = orderApplicationService;
        this.paymentApplicationService = paymentApplicationService;
    }

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

    @GetMapping("/{orderId}")
    public OrderView getOrder(@PathVariable UUID orderId) {
        return orderApplicationService.getOrder(orderId);
    }

    @PostMapping("/{orderId}/payments")
    public OrderView simulatePayment(
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SimulatePaymentRequest request) {
        return paymentApplicationService.processPayment(
                orderId, idempotencyKey, request.outcome());
    }

    @PostMapping("/{orderId}/cancellations")
    public OrderView cancelOrder(@PathVariable UUID orderId) {
        return orderApplicationService.cancelOrder(orderId);
    }
}
