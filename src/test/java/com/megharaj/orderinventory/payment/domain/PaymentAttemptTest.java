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
        assertThat(attempt.getProcessingToken()).isNull();
        assertThat(attempt.getLeaseExpiresAt()).isNull();
        assertThatThrownBy(() -> attempt.succeed("provider-456", NOW.plusSeconds(2)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void recordsFailureReason() {
        PaymentAttempt attempt = newAttempt();

        attempt.fail("provider-123", "declined", NOW.plusSeconds(1));

        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(attempt.getFailureReason()).isEqualTo("declined");
        assertThat(attempt.getProcessingToken()).isNull();
        assertThat(attempt.getLeaseExpiresAt()).isNull();
    }

    @Test
    void invalidTakeoverLeaseDoesNotCorruptTheExistingOwner() {
        UUID originalToken = UUID.randomUUID();
        PaymentAttempt attempt = newAttempt(originalToken, NOW.plusSeconds(30));

        assertThatThrownBy(() -> attempt.tryClaim(
                        UUID.randomUUID(),
                        NOW.plusSeconds(30),
                        NOW.plusSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attempt.isOwnedBy(originalToken)).isTrue();
        assertThat(attempt.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void activeLeaseCannotBeClaimedButExpiredLeaseCanBeTakenOver() {
        UUID originalToken = UUID.randomUUID();
        PaymentAttempt attempt = newAttempt(originalToken, NOW.plusSeconds(30));
        UUID replacementToken = UUID.randomUUID();

        assertThat(attempt.tryClaim(
                        replacementToken,
                        NOW.plusSeconds(29),
                        NOW.plusSeconds(59)))
                .isFalse();
        assertThat(attempt.isOwnedBy(originalToken)).isTrue();

        assertThat(attempt.tryClaim(
                        replacementToken,
                        NOW.plusSeconds(30),
                        NOW.plusSeconds(60)))
                .isTrue();
        assertThat(attempt.isOwnedBy(replacementToken)).isTrue();
        assertThat(attempt.getLeaseExpiresAt()).isEqualTo(NOW.plusSeconds(60));
    }

    private static PaymentAttempt newAttempt() {
        return newAttempt(UUID.randomUUID(), NOW.plusSeconds(30));
    }

    private static PaymentAttempt newAttempt(UUID processingToken, Instant leaseExpiresAt) {
        return PaymentAttempt.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payment-key-123",
                PaymentSimulationOutcome.SUCCESS,
                new BigDecimal("29.99"),
                "USD",
                processingToken,
                leaseExpiresAt,
                NOW);
    }
}
