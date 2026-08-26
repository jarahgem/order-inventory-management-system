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
        boolean shouldCharge) {

    PaymentCommand toGatewayCommand() {
        return new PaymentCommand(
                orderId,
                amount,
                currency,
                idempotencyKey,
                requestedOutcome);
    }
}
