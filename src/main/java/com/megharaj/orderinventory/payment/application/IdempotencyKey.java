package com.megharaj.orderinventory.payment.application;

import com.megharaj.orderinventory.common.error.InvalidRequestException;
import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

    private static final Pattern VALID_CHARACTERS = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("Idempotency-Key header must not be blank");
        }
        value = value.trim();
        if (value.length() < 8 || value.length() > 128) {
            throw new InvalidRequestException(
                    "Idempotency-Key must contain between 8 and 128 characters");
        }
        if (!VALID_CHARACTERS.matcher(value).matches()) {
            throw new InvalidRequestException(
                    "Idempotency-Key contains unsupported characters");
        }
    }
}
