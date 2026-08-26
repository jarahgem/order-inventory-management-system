package com.megharaj.orderinventory.payment.application;

public interface PaymentGateway {

    PaymentGatewayResult charge(PaymentCommand command);
}
