package com.megharaj.orderinventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import com.megharaj.orderinventory.order.application.CreateOrderCommand;
import com.megharaj.orderinventory.order.application.CreateOrderLineCommand;
import com.megharaj.orderinventory.order.application.OrderApplicationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.order.domain.OrderStatus;
import com.megharaj.orderinventory.payment.application.PaymentApplicationService;
import com.megharaj.orderinventory.payment.application.PaymentCommand;
import com.megharaj.orderinventory.payment.application.PaymentGateway;
import com.megharaj.orderinventory.payment.application.PaymentGatewayResult;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ConcurrentPaymentIntegrationTest.PaymentGatewayTestConfiguration.class)
class ConcurrentPaymentIntegrationTest extends IntegrationTestSupport {

    private static final int CONCURRENT_RETRIES = 12;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private OrderApplicationService orderService;

    @Autowired
    private PaymentApplicationService paymentService;

    @Autowired
    private CountingPaymentGateway paymentGateway;

    @BeforeEach
    void resetGateway() {
        paymentGateway.reset();
    }

    @Test
    @Timeout(30)
    void concurrentPaymentRetriesCallGatewayOnceAndCommitInventoryOnce() throws Exception {
        CatalogItemView product = createProduct(catalogService, 1);
        OrderView order = placeOrder(orderService, new CreateOrderCommand(
                "concurrent-payment-customer",
                List.of(new CreateOrderLineCommand(product.productId(), 1))));
        String idempotencyKey = paymentKey();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_RETRIES);
        CountDownLatch start = new CountDownLatch(1);

        List<OrderStatus> returnedStatuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_RETRIES)) {
            List<Future<OrderView>> results = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_RETRIES; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start");
                    }
                    return paymentService.processPayment(
                            order.orderId(), idempotencyKey, PaymentSimulationOutcome.SUCCESS);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<OrderView> result : results) {
                returnedStatuses.add(result.get(20, TimeUnit.SECONDS).status());
            }
        }

        assertThat(paymentGateway.invocationCount()).isOne();
        assertThat(returnedStatuses)
                .allMatch(status -> status == OrderStatus.PAYMENT_PENDING
                        || status == OrderStatus.CONFIRMED)
                .contains(OrderStatus.CONFIRMED);
        assertThat(orderService.getOrder(order.orderId()).status()).isEqualTo(OrderStatus.CONFIRMED);

        CatalogItemView inventory = catalogService.getCatalogItem(product.productId());
        assertThat(inventory.availableQuantity()).isZero();
        assertThat(inventory.reservedQuantity()).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentGatewayTestConfiguration {

        @Bean
        @Primary
        CountingPaymentGateway countingPaymentGateway() {
            return new CountingPaymentGateway();
        }
    }

    static final class CountingPaymentGateway implements PaymentGateway {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public PaymentGatewayResult charge(PaymentCommand command) {
            int callNumber = invocations.incrementAndGet();
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Payment simulation was interrupted", exception);
            }
            return PaymentGatewayResult.success("counting-provider-" + callNumber);
        }

        int invocationCount() {
            return invocations.get();
        }

        void reset() {
            invocations.set(0);
        }
    }
}
