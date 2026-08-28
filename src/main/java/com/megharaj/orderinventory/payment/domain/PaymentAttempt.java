package com.megharaj.orderinventory.payment.domain;

import com.megharaj.orderinventory.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_outcome", nullable = false, length = 32)
    private PaymentSimulationOutcome requestedOutcome;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "provider_reference", length = 128)
    private String providerReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentAttempt() {
        // Required by JPA.
    }

    private PaymentAttempt(
            UUID id,
            UUID orderId,
            String idempotencyKey,
            PaymentSimulationOutcome requestedOutcome,
            BigDecimal amount,
            String currency,
            UUID processingToken,
            Instant leaseExpiresAt,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
        this.requestedOutcome = Objects.requireNonNull(requestedOutcome, "requestedOutcome must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        claim(processingToken, now, leaseExpiresAt);
    }

    public static PaymentAttempt start(
            UUID id,
            UUID orderId,
            String idempotencyKey,
            PaymentSimulationOutcome requestedOutcome,
            BigDecimal amount,
            String currency,
            UUID processingToken,
            Instant leaseExpiresAt,
            Instant now) {
        return new PaymentAttempt(
                id,
                orderId,
                idempotencyKey,
                requestedOutcome,
                amount,
                currency,
                processingToken,
                leaseExpiresAt,
                now);
    }

    public boolean tryClaim(UUID newProcessingToken, Instant now, Instant newLeaseExpiresAt) {
        requirePending();
        Objects.requireNonNull(now, "now must not be null");
        if (hasActiveLease(now)) {
            return false;
        }
        claim(newProcessingToken, now, newLeaseExpiresAt);
        return true;
    }

    public boolean isOwnedBy(UUID candidateProcessingToken) {
        return candidateProcessingToken != null && candidateProcessingToken.equals(processingToken);
    }

    public void succeed(String providerReference, Instant now) {
        requirePending();
        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = requireText(providerReference, "providerReference");
        this.completedAt = Objects.requireNonNull(now, "now must not be null");
        clearProcessingLease();
    }

    public void fail(String providerReference, String failureReason, Instant now) {
        requirePending();
        this.status = PaymentStatus.FAILED;
        this.providerReference = requireText(providerReference, "providerReference");
        this.failureReason = requireText(failureReason, "failureReason");
        this.completedAt = Objects.requireNonNull(now, "now must not be null");
        clearProcessingLease();
    }

    private boolean hasActiveLease(Instant now) {
        return processingToken != null
                && leaseExpiresAt != null
                && leaseExpiresAt.isAfter(now);
    }

    private void claim(UUID newProcessingToken, Instant now, Instant newLeaseExpiresAt) {
        UUID validatedToken = Objects.requireNonNull(
                newProcessingToken, "processingToken must not be null");
        Instant validatedNow = Objects.requireNonNull(now, "now must not be null");
        Instant validatedExpiry = Objects.requireNonNull(
                newLeaseExpiresAt, "leaseExpiresAt must not be null");
        if (!validatedExpiry.isAfter(validatedNow)) {
            throw new IllegalArgumentException("leaseExpiresAt must be after now");
        }
        this.processingToken = validatedToken;
        this.leaseExpiresAt = validatedExpiry;
    }

    private void clearProcessingLease() {
        this.processingToken = null;
        this.leaseExpiresAt = null;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new ConflictException("Payment attempt " + id + " has already completed");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentSimulationOutcome getRequestedOutcome() {
        return requestedOutcome;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getProcessingToken() {
        return processingToken;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
