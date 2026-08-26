package com.megharaj.orderinventory.order.application;

import com.megharaj.orderinventory.catalog.domain.Product;
import com.megharaj.orderinventory.catalog.infrastructure.ProductRepository;
import com.megharaj.orderinventory.common.error.ConflictException;
import com.megharaj.orderinventory.common.error.InvalidRequestException;
import com.megharaj.orderinventory.common.error.ResourceNotFoundException;
import com.megharaj.orderinventory.order.domain.CustomerOrder;
import com.megharaj.orderinventory.order.domain.InvalidOrderStateException;
import com.megharaj.orderinventory.order.domain.OrderItemSpec;
import com.megharaj.orderinventory.order.domain.OrderStatus;
import com.megharaj.orderinventory.order.infrastructure.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private static final int MAX_DISTINCT_ITEMS = 100;
    private static final long MAX_QUANTITY_PER_ITEM = 1_000_000L;
    private static final BigDecimal MAX_ORDER_TOTAL = new BigDecimal("99999999999999999.99");

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryReservationService reservationService;
    private final Clock clock;

    public OrderApplicationService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            InventoryReservationService reservationService,
            Clock clock) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.reservationService = reservationService;
        this.clock = clock;
    }

    @Transactional
    public OrderView createAndReserve(CreateOrderCommand command) {
        String customerReference = normalizeCustomerReference(command.customerReference());
        Map<UUID, Long> quantities = normalizeItems(command.items());
        List<OrderItemSpec> itemSpecs = resolveProducts(quantities);
        validateOrderTotal(itemSpecs);

        Instant now = clock.instant();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), customerReference, itemSpecs, now);

        reservationService.reserveAll(order.getItems());
        order.markInventoryReserved(now);
        orderRepository.save(order);
        return OrderViewMapper.toView(order);
    }

    @Transactional(readOnly = true)
    public OrderView getOrder(UUID orderId) {
        CustomerOrder order = orderRepository.findDetailedById(orderId)
                .orElseThrow(() -> orderNotFound(orderId));
        return OrderViewMapper.toView(order);
    }

    @Transactional
    public OrderView cancelOrder(UUID orderId) {
        CustomerOrder order = lockOrder(orderId);
        order.getItems().size();

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return OrderViewMapper.toView(order);
        }
        if (order.getStatus() == OrderStatus.INVENTORY_RESERVED) {
            reservationService.releaseAll(order.getItems());
            order.cancel(clock.instant());
            return OrderViewMapper.toView(order);
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            order.cancel(clock.instant());
            return OrderViewMapper.toView(order);
        }
        throw new InvalidOrderStateException(orderId, order.getStatus(), "be cancelled");
    }

    CustomerOrder lockOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> orderNotFound(orderId));
    }

    private List<OrderItemSpec> resolveProducts(Map<UUID, Long> quantities) {
        List<Product> products = productRepository.findAllById(quantities.keySet());
        Map<UUID, Product> byId = new HashMap<>();
        products.forEach(product -> byId.put(product.getId(), product));

        List<UUID> missing = quantities.keySet().stream()
                .filter(productId -> !byId.containsKey(productId))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Products not found: " + missing);
        }

        List<OrderItemSpec> result = new ArrayList<>(quantities.size());
        quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    Product product = byId.get(entry.getKey());
                    if (!product.isActive()) {
                        throw new ConflictException("Product " + product.getId() + " is inactive");
                    }
                    result.add(new OrderItemSpec(
                            product.getId(),
                            product.getSku(),
                            product.getName(),
                            entry.getValue(),
                            product.getUnitPrice()));
                });
        return List.copyOf(result);
    }

    private static void validateOrderTotal(List<OrderItemSpec> items) {
        BigDecimal total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(MAX_ORDER_TOTAL) > 0) {
            throw new InvalidRequestException(
                    "Order total exceeds the supported maximum of " + MAX_ORDER_TOTAL);
        }
    }

    private static Map<UUID, Long> normalizeItems(List<CreateOrderLineCommand> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new InvalidRequestException("items must contain at least one product");
        }

        Map<UUID, Long> quantities = new LinkedHashMap<>();
        for (CreateOrderLineCommand item : rawItems) {
            if (item == null || item.productId() == null) {
                throw new InvalidRequestException("Each order item must contain a productId");
            }
            if (item.quantity() <= 0 || item.quantity() > MAX_QUANTITY_PER_ITEM) {
                throw new InvalidRequestException(
                        "Each order item quantity must be between 1 and " + MAX_QUANTITY_PER_ITEM);
            }
            try {
                quantities.merge(item.productId(), item.quantity(), Math::addExact);
            } catch (ArithmeticException exception) {
                throw new InvalidRequestException("The combined quantity is too large");
            }
            if (quantities.get(item.productId()) > MAX_QUANTITY_PER_ITEM) {
                throw new InvalidRequestException(
                        "The combined quantity for product " + item.productId()
                                + " exceeds " + MAX_QUANTITY_PER_ITEM);
            }
        }

        if (quantities.size() > MAX_DISTINCT_ITEMS) {
            throw new InvalidRequestException(
                    "An order may contain at most " + MAX_DISTINCT_ITEMS + " distinct products");
        }
        return quantities;
    }

    private static String normalizeCustomerReference(String rawReference) {
        if (rawReference == null || rawReference.isBlank()) {
            throw new InvalidRequestException("customerReference must not be blank");
        }
        String reference = rawReference.trim();
        if (reference.length() > 100) {
            throw new InvalidRequestException("customerReference must contain at most 100 characters");
        }
        return reference;
    }

    private static ResourceNotFoundException orderNotFound(UUID orderId) {
        return new ResourceNotFoundException("Order " + orderId + " was not found");
    }
}
