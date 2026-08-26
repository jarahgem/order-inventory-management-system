package com.megharaj.orderinventory.web.catalog;

import com.megharaj.orderinventory.catalog.application.CreateProductCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
        @PositiveOrZero long initialQuantity) {

    CreateProductCommand toCommand() {
        return new CreateProductCommand(sku, name, unitPrice, initialQuantity);
    }
}
