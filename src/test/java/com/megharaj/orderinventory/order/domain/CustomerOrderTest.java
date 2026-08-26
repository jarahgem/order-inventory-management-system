package com.megharaj.orderinventory.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerOrderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void calculatesTotalAndFollowsTheHappyPathStateMachine() {
        CustomerOrder order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("49.97");

        order.markInventoryReserved(NOW.plusSeconds(1));
        order.beginPayment(NOW.plusSeconds(2));
        order.confirm(NOW.plusSeconds(3));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getUpdatedAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void rejectsInvalidStateTransitions() {
        CustomerOrder order = newOrder();

        assertThatThrownBy(() -> order.confirm(NOW.plusSeconds(1)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    void onlyCreatedOrReservedOrdersCanBeCancelled() {
        CustomerOrder order = newOrder();
        order.markInventoryReserved(NOW.plusSeconds(1));
        order.beginPayment(NOW.plusSeconds(2));

        assertThatThrownBy(() -> order.cancel(NOW.plusSeconds(3)))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PAYMENT_PENDING");
    }

    private static CustomerOrder newOrder() {
        return CustomerOrder.create(
                UUID.randomUUID(),
                "customer-123",
                List.of(
                        new OrderItemSpec(
                                UUID.randomUUID(),
                                "PRODUCT-A",
                                "Product A",
                                2,
                                new BigDecimal("19.99")),
                        new OrderItemSpec(
                                UUID.randomUUID(),
                                "PRODUCT-C",
                                "Product C",
                                1,
                                new BigDecimal("9.99"))),
                NOW);
    }
}
