package com.megharaj.orderinventory.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private boolean active;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // Required by JPA.
    }

    private Product(UUID id, String sku, String name, BigDecimal unitPrice, Instant now) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sku = requireText(sku, "sku");
        this.name = requireText(name, "name");
        this.unitPrice = normalizePrice(unitPrice);
        this.active = true;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static Product create(UUID id, String sku, String name, BigDecimal unitPrice, Instant now) {
        return new Product(id, sku, name, unitPrice, now);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static BigDecimal normalizePrice(BigDecimal value) {
        Objects.requireNonNull(value, "unitPrice must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public boolean isActive() {
        return active;
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
