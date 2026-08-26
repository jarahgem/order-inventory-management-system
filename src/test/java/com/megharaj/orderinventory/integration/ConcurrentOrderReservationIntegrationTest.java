package com.megharaj.orderinventory.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.megharaj.orderinventory.catalog.application.CatalogItemView;
import com.megharaj.orderinventory.catalog.application.CatalogService;
import com.megharaj.orderinventory.order.application.CreateOrderCommand;
import com.megharaj.orderinventory.order.application.CreateOrderLineCommand;
import com.megharaj.orderinventory.order.application.InsufficientInventoryException;
import com.megharaj.orderinventory.order.application.OrderApplicationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentOrderReservationIntegrationTest extends IntegrationTestSupport {

    private static final int AVAILABLE_STOCK = 10;
    private static final int CONCURRENT_ORDERS = 30;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private OrderApplicationService orderService;

    @Test
    @Timeout(30)
    void concurrentOrdersNeverOversellInventory() throws Exception {
        CatalogItemView product = createProduct(catalogService, AVAILABLE_STOCK);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_ORDERS);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ORDERS)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_ORDERS; index++) {
                int customerNumber = index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start");
                    }
                    try {
                        orderService.createAndReserve(new CreateOrderCommand(
                                "concurrent-customer-" + customerNumber,
                                List.of(new CreateOrderLineCommand(product.productId(), 1))));
                        return true;
                    } catch (InsufficientInventoryException expected) {
                        return false;
                    }
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int successfulOrders = 0;
            for (Future<Boolean> result : results) {
                if (result.get(20, TimeUnit.SECONDS)) {
                    successfulOrders++;
                }
            }

            assertThat(successfulOrders).isEqualTo(AVAILABLE_STOCK);
        }

        CatalogItemView finalInventory = catalogService.getCatalogItem(product.productId());
        assertThat(finalInventory.availableQuantity()).isZero();
        assertThat(finalInventory.reservedQuantity()).isEqualTo(AVAILABLE_STOCK);
        assertThat(finalInventory.availableQuantity()).isNotNegative();
    }
}
