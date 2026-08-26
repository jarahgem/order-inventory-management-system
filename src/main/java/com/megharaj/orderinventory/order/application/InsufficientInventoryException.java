package com.megharaj.orderinventory.order.application;

import com.megharaj.orderinventory.common.error.ConflictException;
import java.util.UUID;

public class InsufficientInventoryException extends ConflictException {

    private final UUID productId;
    private final long requestedQuantity;
    private final long availableQuantity;

    public InsufficientInventoryException(
            UUID productId,
            long requestedQuantity,
            long availableQuantity) {
        super("Insufficient inventory for product " + productId
                + ": requested " + requestedQuantity
                + ", available " + availableQuantity);
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public UUID getProductId() {
        return productId;
    }

    public long getRequestedQuantity() {
        return requestedQuantity;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }
}
