package com.megharaj.orderinventory.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.megharaj.orderinventory.common.error.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void recordsSuccessfulCompletionOnlyOnce() {
        PaymentAttempt attempt = newAttempt();

        attempt.succeed("provider-123", NOW.plusSeconds(1));

        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(attempt.getProviderReference()).isEqualTo("provider-123");
        assertThat(attempt.getCompletedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> attempt.succeed("provider-456", NOW.plusSeconds(2)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void recordsFailureReason() {
        PaymentAttempt attempt = newAttempt();

        attempt.fail("provider-123", "declined", NOW.plusSeconds(1));

        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(attempt.getFailureReason()).isEqualTo("declined");
    }

    private static PaymentAttempt newAttempt() {
        return PaymentAttempt.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payment-key-123",
                PaymentSimulationOutcome.SUCCESS,
                new BigDecimal("29.99"),
                "USD",
                NOW);
    }
}
