package com.megharaj.orderinventory.payment.application;

public record PaymentGatewayResult(
        boolean successful,
        String providerReference,
        String failureReason) {

    public static PaymentGatewayResult success(String providerReference) {
        return new PaymentGatewayResult(true, providerReference, null);
    }

    public static PaymentGatewayResult failure(String providerReference, String failureReason) {
        return new PaymentGatewayResult(false, providerReference, failureReason);
    }
}
