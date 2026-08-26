package com.megharaj.orderinventory.order.application;

import java.util.List;

public record CreateOrderCommand(String customerReference, List<CreateOrderLineCommand> items) {
}
