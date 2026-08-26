package com.megharaj.orderinventory.web.order;

import com.megharaj.orderinventory.order.application.CreateOrderLineCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderLineRequest(
        @NotNull UUID productId,
        @Min(1) @Max(1_000_000) long quantity) {

    CreateOrderLineCommand toCommand() {
        return new CreateOrderLineCommand(productId, quantity);
    }
}
