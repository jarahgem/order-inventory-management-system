package com.megharaj.orderinventory.order.application;

import java.util.UUID;

public record CreateOrderLineCommand(UUID productId, long quantity) {
}
