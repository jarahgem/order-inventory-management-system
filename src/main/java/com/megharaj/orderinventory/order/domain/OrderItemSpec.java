package com.megharaj.orderinventory.order.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record OrderItemSpec(
        UUID productId,
        String productSku,
        String productName,
        long quantity,
        BigDecimal unitPrice) {

    public OrderItemSpec {
        Objects.requireNonNull(productId, "productId must not be null");
        if (productSku == null || productSku.isBlank()) {
            throw new IllegalArgumentException("productSku must not be blank");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }
}
