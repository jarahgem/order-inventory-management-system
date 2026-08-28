package com.megharaj.orderinventory.integration;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import com.megharaj.orderinventory.catalog.application.CreateProductCommand;
import com.megharaj.orderinventory.common.IdempotencyKey;
import com.megharaj.orderinventory.order.application.CreateOrderCommand;
import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import java.math.BigDecimal;
import java.util.UUID;

abstract class IntegrationTestSupport {

    protected CatalogItemView createProduct(CatalogService catalogService, long initialQuantity) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return catalogService.createProduct(new CreateProductCommand(
                "TEST-" + suffix,
                "Test Product " + suffix,
                new BigDecimal("12.50"),
                initialQuantity));
    }

    protected OrderView placeOrder(OrderApplicationService orderService, CreateOrderCommand command) {
        return orderService.placeOrder(command, new IdempotencyKey("order-" + UUID.randomUUID()));
    }

    protected String paymentKey() {
        return "payment-" + UUID.randomUUID();
    }
}
