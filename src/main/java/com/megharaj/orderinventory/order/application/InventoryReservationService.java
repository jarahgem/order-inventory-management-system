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

@Service
public class InventoryReservationService {

    private final InventoryRepository inventoryRepository;

    public InventoryReservationService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseAll(List<OrderItem> items) {
        for (OrderItem item : sorted(items)) {
            int updated = inventoryRepository.releaseReservation(item.getProductId(), item.getQuantity());
            if (updated != 1) {
                throw consistencyFailure("release", item);
            }
        }
    }

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
