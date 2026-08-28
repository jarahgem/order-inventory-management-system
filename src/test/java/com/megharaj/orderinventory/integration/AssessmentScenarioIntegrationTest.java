package com.megharaj.orderinventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import com.megharaj.orderinventory.catalog.application.CreateProductCommand;
import com.megharaj.orderinventory.order.application.CreateOrderCommand;
import com.megharaj.orderinventory.order.application.CreateOrderLineCommand;
import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.order.domain.OrderStatus;
import com.megharaj.orderinventory.payment.application.PaymentApplicationService;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AssessmentScenarioIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private OrderApplicationService orderService;

    @Autowired
    private PaymentApplicationService paymentService;

    @Test
    void exactAssessmentBasketIsReservedReleasedOnFailureAndConsumedOnSuccess() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        CatalogItemView productA = createProduct("ASSESS-A-" + suffix, "Product A", "19.99", 10);
        CatalogItemView productB = createProduct("ASSESS-B-" + suffix, "Product B", "29.99", 5);
        CatalogItemView productC = createProduct("ASSESS-C-" + suffix, "Product C", "9.99", 20);

        OrderView firstOrder = createAssessmentOrder(productA, productB, productC, "failure");

        assertThat(firstOrder.status()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        assertThat(firstOrder.totalAmount()).isEqualByComparingTo("119.92");
        assertInventory(productA, 8, 2);
        assertInventory(productB, 4, 1);
        assertInventory(productC, 15, 5);

        OrderView failed = paymentService.processPayment(
                firstOrder.orderId(), paymentKey(), PaymentSimulationOutcome.FAILURE);

        assertThat(failed.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertInventory(productA, 10, 0);
        assertInventory(productB, 5, 0);
        assertInventory(productC, 20, 0);

        OrderView secondOrder = createAssessmentOrder(productA, productB, productC, "success");
        OrderView confirmed = paymentService.processPayment(
                secondOrder.orderId(), paymentKey(), PaymentSimulationOutcome.SUCCESS);

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertInventory(productA, 8, 0);
        assertInventory(productB, 4, 0);
        assertInventory(productC, 15, 0);
    }

    private CatalogItemView createProduct(
            String sku,
            String name,
            String unitPrice,
            long initialQuantity) {
        return catalogService.createProduct(new CreateProductCommand(
                sku,
                name,
                new BigDecimal(unitPrice),
                initialQuantity));
    }

    private OrderView createAssessmentOrder(
            CatalogItemView productA,
            CatalogItemView productB,
            CatalogItemView productC,
            String referenceSuffix) {
        return orderService.createAndReserve(new CreateOrderCommand(
                "assessment-" + referenceSuffix + "-" + UUID.randomUUID(),
                List.of(
                        new CreateOrderLineCommand(productA.productId(), 2),
                        new CreateOrderLineCommand(productB.productId(), 1),
                        new CreateOrderLineCommand(productC.productId(), 5))));
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
