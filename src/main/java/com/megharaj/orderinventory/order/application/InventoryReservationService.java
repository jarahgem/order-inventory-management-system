package com.megharaj.orderinventory.order.application;

import com.megharaj.orderinventory.catalog.domain.Inventory;
import com.megharaj.orderinventory.catalog.infrastructure.InventoryRepository;
import com.megharaj.orderinventory.common.error.InventoryConsistencyException;
import com.megharaj.orderinventory.order.domain.OrderItem;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates atomic inventory reservation mutations for a complete order.
 *
 * <p>Every public method requires an existing transaction. Product identifiers are processed in
 * deterministic order to reduce deadlock risk when concurrent orders touch the same rows.
 */
@Service
public class InventoryReservationService {

    private final InventoryRepository inventoryRepository;

    /**
     * Creates the reservation service.
     *
     * @param inventoryRepository repository exposing guarded inventory mutations
     */
    public InventoryReservationService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Atomically moves every requested quantity from available to reserved inventory.
     *
     * <p>If any product cannot be reserved, the exception marks the caller's transaction for
     * rollback, so earlier item updates are not retained.
     *
     * @param items order-item snapshots to reserve
     * @throws InsufficientInventoryException when any product lacks the requested quantity
     * @throws org.springframework.transaction.IllegalTransactionStateException when called
     *     without an active transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveAll(List<OrderItem> items) {
        for (OrderItem item : sorted(items)) {
            int updated = inventoryRepository.reserveAtomically(item.getProductId(), item.getQuantity());
            if (updated != 1) {
                long available = inventoryRepository.findById(item.getProductId())
                        .map(Inventory::getAvailableQuantity)
                        .orElse(0L);
                throw new InsufficientInventoryException(
                        item.getProductId(), item.getQuantity(), available);
            }
        }
    }

    /**
     * Returns all reserved quantities to available inventory after cancellation or payment
     * failure.
     *
     * @param items order-item snapshots whose reservations must be released
     * @throws InventoryConsistencyException when a guarded release cannot update exactly one row
     * @throws org.springframework.transaction.IllegalTransactionStateException when called
     *     without an active transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseAll(List<OrderItem> items) {
        for (OrderItem item : sorted(items)) {
            int updated = inventoryRepository.releaseReservation(item.getProductId(), item.getQuantity());
            if (updated != 1) {
                throw consistencyFailure("release", item);
            }
        }
    }

    /**
     * Consumes all reserved quantities after successful payment.
     *
     * <p>Available inventory was already reduced during reservation; committing therefore
     * decrements only the reserved quantity.
     *
     * @param items order-item snapshots whose reservations must be consumed
     * @throws InventoryConsistencyException when a guarded commit cannot update exactly one row
     * @throws org.springframework.transaction.IllegalTransactionStateException when called
     *     without an active transaction
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commitAll(List<OrderItem> items) {
        for (OrderItem item : sorted(items)) {
            int updated = inventoryRepository.commitReservation(item.getProductId(), item.getQuantity());
            if (updated != 1) {
                throw consistencyFailure("commit", item);
            }
        }
    }

    private static List<OrderItem> sorted(List<OrderItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(item -> item.getProductId().toString()))
                .toList();
    }

    private static InventoryConsistencyException consistencyFailure(String action, OrderItem item) {
        UUID productId = item.getProductId();
        return new InventoryConsistencyException(
                "Could not " + action + " reservation for product " + productId
                        + " and quantity " + item.getQuantity());
    }
}
