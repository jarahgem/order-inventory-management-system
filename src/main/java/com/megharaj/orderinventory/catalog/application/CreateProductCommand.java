package com.megharaj.orderinventory.catalog.application;

import java.math.BigDecimal;

public record CreateProductCommand(
        String sku,
        String name,
        BigDecimal unitPrice,
        long initialQuantity) {
}
