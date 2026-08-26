package com.megharaj.orderinventory.order.application;

import com.megharaj.orderinventory.order.domain.CustomerOrder;
import com.megharaj.orderinventory.order.domain.OrderItem;
import java.util.List;

public final class OrderViewMapper {

    private OrderViewMapper() {
    }

    public static OrderView toView(CustomerOrder order) {
        List<OrderItemView> items = order.getItems().stream()
                .map(OrderViewMapper::toItemView)
                .toList();
        return new OrderView(
                order.getId(),
                order.getCustomerReference(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private static OrderItemView toItemView(OrderItem item) {
        return new OrderItemView(
                item.getProductId(),
                item.getProductSku(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.lineTotal());
    }
}
