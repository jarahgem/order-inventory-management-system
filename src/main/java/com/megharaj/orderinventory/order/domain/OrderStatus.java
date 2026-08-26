package com.megharaj.orderinventory.order.domain;

public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED
}
