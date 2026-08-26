package com.megharaj.orderinventory.web.catalog;

import jakarta.validation.constraints.Positive;

public record RestockRequest(@Positive long quantity) {
}
