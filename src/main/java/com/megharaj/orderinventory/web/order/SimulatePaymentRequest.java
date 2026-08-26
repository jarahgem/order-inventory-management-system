package com.megharaj.orderinventory.web.order;

import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(@NotNull PaymentSimulationOutcome outcome) {
}
