package com.megharaj.orderinventory.catalog.infrastructure;

import com.megharaj.orderinventory.catalog.domain.Inventory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity - :quantity,
                   reserved_quantity = reserved_quantity + :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE product_id = :productId
               AND available_quantity >= :quantity
            """, nativeQuery = true)
    int reserveAtomically(
            @Param("productId") UUID productId,
            @Param("quantity") long quantity);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity + :quantity,
                   reserved_quantity = reserved_quantity - :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE product_id = :productId
               AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int releaseReservation(
            @Param("productId") UUID productId,
            @Param("quantity") long quantity);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET reserved_quantity = reserved_quantity - :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE product_id = :productId
               AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int commitReservation(
            @Param("productId") UUID productId,
            @Param("quantity") long quantity);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity + :quantity,
                   version = version + 1,
                   updated_at = CURRENT_TIMESTAMP
             WHERE product_id = :productId
            """, nativeQuery = true)
    int restock(
            @Param("productId") UUID productId,
            @Param("quantity") long quantity);
}
