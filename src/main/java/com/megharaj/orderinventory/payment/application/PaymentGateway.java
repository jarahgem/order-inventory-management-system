package com.megharaj.orderinventory.payment.application;

/**
 * Port implemented by payment-provider adapters.
 *
 * <p>Implementations must honor the supplied idempotency key so a lease takeover can safely retry
 * an uncertain provider call.
 */
public interface PaymentGateway {

    /**
     * Requests one idempotent charge from the provider.
     *
     * @param command order, amount, currency, key, and requested simulator outcome
     * @return normalized provider result
     */
    PaymentGatewayResult charge(PaymentCommand command);
}
