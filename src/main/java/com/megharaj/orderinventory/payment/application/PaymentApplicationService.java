package com.megharaj.orderinventory.payment.application;

import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentApplicationService {

    private final PaymentTransactionService transactionService;
    private final PaymentGateway paymentGateway;
    private final OrderApplicationService orderApplicationService;

    public PaymentApplicationService(
            PaymentTransactionService transactionService,
            PaymentGateway paymentGateway,
            OrderApplicationService orderApplicationService) {
        this.transactionService = transactionService;
        this.paymentGateway = paymentGateway;
        this.orderApplicationService = orderApplicationService;
    }

    public OrderView processPayment(
            UUID orderId,
            String rawIdempotencyKey,
            PaymentSimulationOutcome requestedOutcome) {
        Objects.requireNonNull(requestedOutcome, "requestedOutcome must not be null");
        IdempotencyKey idempotencyKey = new IdempotencyKey(rawIdempotencyKey);
        PaymentPreparation preparation = transactionService.prepare(
                orderId, idempotencyKey, requestedOutcome);

        if (!preparation.shouldCharge()) {
            return orderApplicationService.getOrder(orderId);
        }

        PaymentGatewayResult result = paymentGateway.charge(preparation.toGatewayCommand());
        return transactionService.complete(preparation, result);
    }
}
