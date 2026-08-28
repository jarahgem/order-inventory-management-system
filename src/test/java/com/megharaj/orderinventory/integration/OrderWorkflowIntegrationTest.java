package com.megharaj.orderinventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import com.megharaj.orderinventory.common.IdempotencyKey;
import com.megharaj.orderinventory.common.error.ConflictException;
import com.megharaj.orderinventory.order.application.CreateOrderCommand;
import com.megharaj.orderinventory.order.application.CreateOrderLineCommand;
import com.megharaj.orderinventory.order.application.InsufficientInventoryException;
import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.order.domain.OrderStatus;
import com.megharaj.orderinventory.order.infrastructure.OrderRepository;
import com.megharaj.orderinventory.payment.application.PaymentApplicationService;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import com.megharaj.orderinventory.payment.domain.PaymentStatus;
import com.megharaj.orderinventory.payment.infrastructure.PaymentAttemptRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private OrderApplicationService orderService;

    @Autowired
    private PaymentApplicationService paymentService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Test
    void repeatingOrderCreationWithTheSameKeyReplaysTheOriginalOrder() {
        CatalogItemView product = createProduct(catalogService, 5);
        IdempotencyKey key = new IdempotencyKey("order-" + java.util.UUID.randomUUID());
        CreateOrderCommand command = new CreateOrderCommand(
                "idempotent-customer",
                List.of(new CreateOrderLineCommand(product.productId(), 2)));
        long ordersBefore = orderRepository.count();

        OrderView first = orderService.placeOrder(command, key);
        OrderView replay = orderService.placeOrder(command, key);

        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertInventory(product, 3, 2);
    }

    @Test
    void successfulPaymentConfirmsOrderAndConsumesReservedStockExactlyOnce() {
        CatalogItemView product = createProduct(catalogService, 5);
        OrderView order = createOrder(product, 2);
        String idempotencyKey = paymentKey();

        assertInventory(product, 3, 2);

        OrderView confirmed = paymentService.processPayment(
                order.orderId(), idempotencyKey, PaymentSimulationOutcome.SUCCESS);
        OrderView retried = paymentService.processPayment(
                order.orderId(), idempotencyKey, PaymentSimulationOutcome.SUCCESS);

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(retried.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertInventory(product, 3, 0);
        assertThat(paymentAttemptRepository.findByOrderId(order.orderId()))
                .hasValueSatisfying(attempt ->
                        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED));
    }

    @Test
    void failedPaymentReleasesInventory() {
        CatalogItemView product = createProduct(catalogService, 5);
        OrderView order = createOrder(product, 2);

        OrderView failed = paymentService.processPayment(
                order.orderId(), paymentKey(), PaymentSimulationOutcome.FAILURE);

        assertThat(failed.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertInventory(product, 5, 0);
        assertThat(paymentAttemptRepository.findByOrderId(order.orderId()))
                .hasValueSatisfying(attempt ->
                        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.FAILED));
    }

    @Test
    void failedPaymentReleasesEveryItemInAMultiItemOrder() {
        CatalogItemView firstProduct = createProduct(catalogService, 4);
        CatalogItemView secondProduct = createProduct(catalogService, 7);
        OrderView order = placeOrder(orderService, new CreateOrderCommand(
                "multi-item-payment-failure",
                List.of(
                        new CreateOrderLineCommand(firstProduct.productId(), 2),
                        new CreateOrderLineCommand(secondProduct.productId(), 3))));

        OrderView failed = paymentService.processPayment(
                order.orderId(), paymentKey(), PaymentSimulationOutcome.FAILURE);

        assertThat(failed.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertInventory(firstProduct, 4, 0);
        assertInventory(secondProduct, 7, 0);
    }

    @Test
    void cancellationReleasesInventoryAndIsIdempotent() {
        CatalogItemView product = createProduct(catalogService, 5);
        OrderView order = createOrder(product, 2);

        OrderView cancelled = orderService.cancelOrder(order.orderId());
        OrderView retried = orderService.cancelOrder(order.orderId());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(retried.status()).isEqualTo(OrderStatus.CANCELLED);
        assertInventory(product, 5, 0);
    }

    @Test
    void multiItemReservationRollsBackWhenAnyProductIsUnavailable() {
        CatalogItemView availableProduct = createProduct(catalogService, 2);
        CatalogItemView unavailableProduct = createProduct(catalogService, 0);
        long orderCountBefore = orderRepository.count();

        CreateOrderCommand command = new CreateOrderCommand(
                "rollback-customer",
                List.of(
                        new CreateOrderLineCommand(availableProduct.productId(), 2),
                        new CreateOrderLineCommand(unavailableProduct.productId(), 1)));

        assertThatThrownBy(() -> placeOrder(orderService, command))
                .isInstanceOf(InsufficientInventoryException.class);

        assertInventory(availableProduct, 2, 0);
        assertInventory(unavailableProduct, 0, 0);
        assertThat(orderRepository.count()).isEqualTo(orderCountBefore);
    }

    @Test
    void aCompletedOrderCannotBeCancelledOrPaidWithAnotherKey() {
        CatalogItemView product = createProduct(catalogService, 1);
        OrderView order = createOrder(product, 1);
        String originalKey = paymentKey();
        paymentService.processPayment(
                order.orderId(), originalKey, PaymentSimulationOutcome.SUCCESS);

        assertThatThrownBy(() -> orderService.cancelOrder(order.orderId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> paymentService.processPayment(
                order.orderId(), paymentKey(), PaymentSimulationOutcome.SUCCESS))
                .isInstanceOf(ConflictException.class);
        assertInventory(product, 0, 0);
    }

    @Test
    void duplicateLinesAreCombinedBeforeReservation() {
        CatalogItemView product = createProduct(catalogService, 5);

        OrderView order = placeOrder(orderService, new CreateOrderCommand(
                "duplicate-lines-customer",
                List.of(
                        new CreateOrderLineCommand(product.productId(), 1),
                        new CreateOrderLineCommand(product.productId(), 2))));

        assertThat(order.items()).hasSize(1);
        assertThat(order.items().getFirst().quantity()).isEqualTo(3);
        assertInventory(product, 2, 3);
    }

    private OrderView createOrder(CatalogItemView product, long quantity) {
        return placeOrder(orderService, new CreateOrderCommand(
                "customer-" + product.productId(),
                List.of(new CreateOrderLineCommand(product.productId(), quantity))));
    }

    private void assertInventory(
            CatalogItemView product,
            long expectedAvailable,
            long expectedReserved) {
        CatalogItemView current = catalogService.getCatalogItem(product.productId());
        assertThat(current.availableQuantity()).isEqualTo(expectedAvailable);
        assertThat(current.reservedQuantity()).isEqualTo(expectedReserved);
    }
}
