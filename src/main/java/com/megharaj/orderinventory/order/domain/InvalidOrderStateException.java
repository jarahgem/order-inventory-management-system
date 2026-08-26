package com.megharaj.orderinventory.order.domain;

import com.megharaj.orderinventory.common.error.ConflictException;
import java.util.UUID;

public class InvalidOrderStateException extends ConflictException {

    public InvalidOrderStateException(UUID orderId, OrderStatus currentStatus, String operation) {
        super("Order " + orderId + " cannot " + operation + " while in status " + currentStatus);
    }
}
