package com.megharaj.orderinventory.payment.application;

import com.megharaj.orderinventory.common.error.InvalidRequestException;
import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the payment workflow across short database transactions and the gateway port.
 *
 * <p>The remote gateway call deliberately runs outside a database transaction. Transactional
 * preparation grants one request a processing lease; completion then commits or releases stock.
 */
@Service
public class PaymentApplicationService {

    private final PaymentTransactionService transactionService;
    private final PaymentGateway paymentGateway;
    private final OrderApplicationService orderApplicationService;

    /**
     * Creates the payment orchestration service.
     *
     * @param transactionService short transactional payment stages
     * @param paymentGateway payment provider port
     * @param orderApplicationService read service used by non-owning idempotent retries
     */
    public PaymentApplicationService(
            PaymentTransactionService transactionService,
            PaymentGateway paymentGateway,
            OrderApplicationService orderApplicationService) {
        this.transactionService = transactionService;
        this.paymentGateway = paymentGateway;
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * Processes or safely retries a simulated payment for an inventory-reserved order.
     *
     * <p>The first request that owns the processing lease calls the gateway. Concurrent or
     * completed retries return the current order without creating another charge.
     *
     * @param orderId order being paid
     * @param rawIdempotencyKey client-provided key reused for every retry
     * @param requestedOutcome requested simulator result
     * @return current order state after preparation or payment completion
     * @throws InvalidRequestException when the outcome or idempotency key is invalid
     */
    public OrderView processPayment(
            UUID orderId,
            String rawIdempotencyKey,
            PaymentSimulationOutcome requestedOutcome) {
        if (requestedOutcome == null) {
            throw new InvalidRequestException("outcome must not be null");
        }
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
