package com.megharaj.orderinventory.web.order;

import com.megharaj.orderinventory.order.application.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String customerReference,
        @NotEmpty @Size(max = 100) List<@Valid OrderLineRequest> items) {

    CreateOrderCommand toCommand() {
        return new CreateOrderCommand(
                customerReference,
                items.stream().map(OrderLineRequest::toCommand).toList());
    }
}
