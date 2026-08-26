package com.megharaj.orderinventory.payment.infrastructure;

import com.megharaj.orderinventory.payment.domain.PaymentAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    Optional<PaymentAttempt> findByOrderId(UUID orderId);

    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);
}
