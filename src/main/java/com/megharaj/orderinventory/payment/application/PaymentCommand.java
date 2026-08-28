package com.megharaj.orderinventory.payment.application;

import com.megharaj.orderinventory.common.IdempotencyKey;

import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCommand(
        UUID orderId,
        BigDecimal amount,
        String currency,
        IdempotencyKey idempotencyKey,
        PaymentSimulationOutcome requestedOutcome) {
}
