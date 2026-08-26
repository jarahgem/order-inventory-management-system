package com.megharaj.orderinventory.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available_quantity", nullable = false)
    private long availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
        // Required by JPA.
    }

    private Inventory(UUID productId, long availableQuantity, Instant now) {
        if (availableQuantity < 0) {
            throw new IllegalArgumentException("availableQuantity must not be negative");
        }
        this.productId = Objects.requireNonNull(productId, "productId must not be null");
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public static Inventory create(UUID productId, long availableQuantity, Instant now) {
        return new Inventory(productId, availableQuantity, now);
    }

    public UUID getProductId() {
        return productId;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
