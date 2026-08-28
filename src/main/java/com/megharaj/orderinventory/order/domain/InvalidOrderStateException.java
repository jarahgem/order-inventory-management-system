package com.megharaj.orderinventory.order.domain;

import com.megharaj.orderinventory.common.error.ConflictException;
import java.util.UUID;

public class InvalidOrderStateException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public InvalidOrderStateException(UUID orderId, OrderStatus currentStatus, String operation) {
        super("Order " + orderId + " cannot " + operation + " while in status " + currentStatus);
    }
}
