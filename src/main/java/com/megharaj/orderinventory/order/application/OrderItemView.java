package com.megharaj.orderinventory.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemView(
        UUID productId,
        String sku,
        String name,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
