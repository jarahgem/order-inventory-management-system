package com.megharaj.orderinventory.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    public static final String DEFAULT_CURRENCY = "USD";

    @Id
    private UUID id;

    @Column(name = "customer_reference", nullable = false, length = 100)
    private String customerReference;

    @Column(name = "idempotency_key", length = 128, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productSku ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerOrder() {
        // Required by JPA.
    }

    private CustomerOrder(
            UUID id,
            String customerReference,
            String idempotencyKey,
            List<OrderItemSpec> itemSpecs,
            Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        if (customerReference == null || customerReference.isBlank()) {
            throw new IllegalArgumentException("customerReference must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (itemSpecs == null || itemSpecs.isEmpty()) {
            throw new IllegalArgumentException("An order must contain at least one item");
        }
        this.customerReference = customerReference;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.CREATED;
        this.currency = DEFAULT_CURRENCY;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        itemSpecs.forEach(spec -> this.items.add(new OrderItem(this, spec)));
        this.totalAmount = this.items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public static CustomerOrder create(
            UUID id,
            String customerReference,
            String idempotencyKey,
            List<OrderItemSpec> itemSpecs,
            Instant now) {
        return new CustomerOrder(id, customerReference, idempotencyKey, itemSpecs, now);
    }

    public void markInventoryReserved(Instant now) {
        requireStatus(OrderStatus.CREATED, "reserve inventory");
        transitionTo(OrderStatus.INVENTORY_RESERVED, now);
    }

    public void beginPayment(Instant now) {
        requireStatus(OrderStatus.INVENTORY_RESERVED, "start payment");
        transitionTo(OrderStatus.PAYMENT_PENDING, now);
    }

    public void confirm(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING, "confirm");
        transitionTo(OrderStatus.CONFIRMED, now);
    }

    public void markPaymentFailed(Instant now) {
        requireStatus(OrderStatus.PAYMENT_PENDING, "record payment failure");
        transitionTo(OrderStatus.PAYMENT_FAILED, now);
    }

    public void cancel(Instant now) {
        if (status != OrderStatus.CREATED && status != OrderStatus.INVENTORY_RESERVED) {
            throw new InvalidOrderStateException(id, status, "be cancelled");
        }
        transitionTo(OrderStatus.CANCELLED, now);
    }

    private void requireStatus(OrderStatus required, String operation) {
        if (status != required) {
            throw new InvalidOrderStateException(id, status, operation);
        }
    }

    private void transitionTo(OrderStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
