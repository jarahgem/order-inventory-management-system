package com.megharaj.orderinventory.payment.infrastructure;

import com.megharaj.orderinventory.payment.application.PaymentCommand;
import com.megharaj.orderinventory.payment.application.PaymentGateway;
import com.megharaj.orderinventory.payment.application.PaymentGatewayResult;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic local payment adapter used by the assessment and integration tests.
 *
 * <p>The provider reference is derived from the order and idempotency key, so retries return the
 * same external identifier.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    /** {@inheritDoc} */
    @Override
    public PaymentGatewayResult charge(PaymentCommand command) {
        String source = command.orderId() + ":" + command.idempotencyKey().value();
        String providerReference = "sim_" + UUID.nameUUIDFromBytes(
                source.getBytes(StandardCharsets.UTF_8));

        if (command.requestedOutcome() == PaymentSimulationOutcome.SUCCESS) {
            return PaymentGatewayResult.success(providerReference);
        }
        return PaymentGatewayResult.failure(providerReference, "Simulated payment decline");
    }
}
