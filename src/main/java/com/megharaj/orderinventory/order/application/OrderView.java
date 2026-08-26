package com.megharaj.orderinventory.order.application;

import com.megharaj.orderinventory.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(
        UUID orderId,
        String customerReference,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemView> items,
        Instant createdAt,
        Instant updatedAt) {
}
