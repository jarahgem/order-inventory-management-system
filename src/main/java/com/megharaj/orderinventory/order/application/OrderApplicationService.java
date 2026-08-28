package com.megharaj.orderinventory.order.application;

import com.megharaj.orderinventory.catalog.domain.Product;
import com.megharaj.orderinventory.catalog.infrastructure.ProductRepository;
import com.megharaj.orderinventory.common.IdempotencyKey;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for creating, reading, and cancelling orders.
 *
 * <p>The service normalizes duplicate lines, snapshots product data into the order aggregate,
 * and coordinates inventory reservations inside explicit transaction boundaries.
 */
@Service
public class OrderApplicationService {

    private static final int MAX_DISTINCT_ITEMS = 100;
    private static final long MAX_QUANTITY_PER_ITEM = 1_000_000L;
    private static final BigDecimal MAX_ORDER_TOTAL = new BigDecimal("99999999999999999.99");

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final InventoryReservationService reservationService;
    private final OrderApplicationService self;
    private final Clock clock;

    /**
     * Creates the order application service.
     *
     * @param productRepository product lookup gateway
     * @param orderRepository order persistence gateway
     * @param reservationService atomic inventory mutation service
     * @param self lazy self-reference used to invoke transactional creation through the proxy
     * @param clock source of order transition timestamps
     */
    public OrderApplicationService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            InventoryReservationService reservationService,
            @Lazy OrderApplicationService self,
            Clock clock) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.reservationService = reservationService;
        this.self = self;
        this.clock = clock;
    }

    /**
     * Places an order idempotently: a repeated {@code Idempotency-Key} returns the original order
     * instead of creating or reserving inventory for a duplicate.
     *
     * <p>A prior order for the key is replayed without side effects. Otherwise creation proceeds in
     * its own transaction; if a concurrent request wins the unique-key race, this request's
     * reservation rolls back and the winning order is returned.
     *
     * @param command customer reference and requested product quantities
     * @param idempotencyKey validated client-supplied key that identifies this order
     * @return the order view for the new or previously created order
     * @throws InvalidRequestException when the request violates item, quantity, reference, or
     *     monetary limits
     * @throws ResourceNotFoundException when any referenced product does not exist
     * @throws ConflictException when a referenced product is inactive
     * @throws InsufficientInventoryException when any requested quantity is unavailable
     */
    public OrderView placeOrder(CreateOrderCommand command, IdempotencyKey idempotencyKey) {
        return findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> createOrReplay(command, idempotencyKey));
    }

    private OrderView createOrReplay(CreateOrderCommand command, IdempotencyKey idempotencyKey) {
        try {
            return self.createAndReserve(command, idempotencyKey);
        } catch (DataIntegrityViolationException duplicateKey) {
            return findByIdempotencyKey(idempotencyKey).orElseThrow(() -> duplicateKey);
        }
    }

    private Optional<OrderView> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return orderRepository.findDetailedByIdempotencyKey(idempotencyKey.value())
                .map(OrderViewMapper::toView);
    }

    /**
     * Creates an order and reserves all requested inventory in one transaction.
     *
     * <p>Duplicate product lines are combined, inactive or missing products are rejected, and the
     * order is saved only after every reservation succeeds. Prefer {@link #placeOrder} so that
     * retries are idempotent; a duplicate {@code Idempotency-Key} surfaces here as a
     * {@link DataIntegrityViolationException}.
     *
     * @param command customer reference and requested product quantities
     * @param idempotencyKey validated client-supplied key persisted with the order
     * @return order view with status {@code INVENTORY_RESERVED}
     * @throws InvalidRequestException when the request violates item, quantity, reference, or
     *     monetary limits
     * @throws ResourceNotFoundException when any referenced product does not exist
     * @throws ConflictException when a referenced product is inactive
     * @throws InsufficientInventoryException when any requested quantity is unavailable
     */
    @Transactional
    public OrderView createAndReserve(CreateOrderCommand command, IdempotencyKey idempotencyKey) {
        String customerReference = normalizeCustomerReference(command.customerReference());
        Map<UUID, Long> quantities = normalizeItems(command.items());
        List<OrderItemSpec> itemSpecs = resolveProducts(quantities);
        validateOrderTotal(itemSpecs);

        Instant now = clock.instant();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(), customerReference, idempotencyKey.value(), itemSpecs, now);

        reservationService.reserveAll(order.getItems());
        order.markInventoryReserved(now);
        orderRepository.save(order);
        return OrderViewMapper.toView(order);
    }

    /**
     * Retrieves an order and eagerly loaded item snapshots.
     *
     * @param orderId order identifier
     * @return current order view
     * @throws ResourceNotFoundException when the order does not exist
     */
    @Transactional(readOnly = true)
    public OrderView getOrder(UUID orderId) {
        CustomerOrder order = orderRepository.findDetailedById(orderId)
                .orElseThrow(() -> orderNotFound(orderId));
        return OrderViewMapper.toView(order);
    }

    /**
     * Cancels an eligible order while serializing against payment through an order-row lock.
     *
     * <p>An inventory-reserved order releases stock before entering {@code CANCELLED}. Repeating
     * the operation for {@code CANCELLED} or {@code PAYMENT_FAILED} is idempotent.
     *
     * @param orderId order identifier
     * @return the resulting order view
     * @throws ResourceNotFoundException when the order does not exist
     * @throws InvalidOrderStateException when the current state cannot be cancelled
     */
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

    /**
     * Loads an order with a pessimistic write lock for state-changing workflows.
     *
     * @param orderId order identifier
     * @return locked order aggregate
     * @throws ResourceNotFoundException when the order does not exist
     */
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
