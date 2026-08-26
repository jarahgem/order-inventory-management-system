package com.megharaj.orderinventory.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CatalogItemView(
        UUID productId,
        String sku,
        String name,
        BigDecimal unitPrice,
        boolean active,
        long availableQuantity,
        long reservedQuantity,
        Instant inventoryUpdatedAt) {
}
