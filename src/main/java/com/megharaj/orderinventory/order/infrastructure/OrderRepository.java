package com.megharaj.orderinventory.order.infrastructure;

import com.megharaj.orderinventory.order.domain.CustomerOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :orderId")
    Optional<CustomerOrder> findDetailedById(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT o FROM CustomerOrder o WHERE o.idempotencyKey = :idempotencyKey")
    Optional<CustomerOrder> findDetailedByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :orderId")
    Optional<CustomerOrder> findByIdForUpdate(@Param("orderId") UUID orderId);
}
