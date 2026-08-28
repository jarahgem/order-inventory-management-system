package com.megharaj.orderinventory.payment.application;

import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.math.BigDecimal;
import java.util.UUID;

record PaymentPreparation(
        UUID attemptId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        IdempotencyKey idempotencyKey,
        PaymentSimulationOutcome requestedOutcome,
        UUID processingToken,
        boolean shouldCharge) {

    PaymentCommand toGatewayCommand() {
        if (!shouldCharge) {
            throw new IllegalStateException("A non-owning payment request cannot call the gateway");
        }
        return new PaymentCommand(
                orderId,
                amount,
                currency,
                idempotencyKey,
                requestedOutcome);
    }
}
