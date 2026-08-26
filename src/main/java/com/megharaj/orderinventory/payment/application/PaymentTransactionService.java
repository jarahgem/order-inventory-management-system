package com.megharaj.orderinventory.payment.application;

import com.megharaj.orderinventory.common.error.ConflictException;
import com.megharaj.orderinventory.common.error.InventoryConsistencyException;
import com.megharaj.orderinventory.common.error.ResourceNotFoundException;
import com.megharaj.orderinventory.order.application.InventoryReservationService;
import com.megharaj.orderinventory.order.application.OrderView;
import com.megharaj.orderinventory.order.application.OrderViewMapper;
import com.megharaj.orderinventory.order.domain.CustomerOrder;
import com.megharaj.orderinventory.order.domain.InvalidOrderStateException;
import com.megharaj.orderinventory.order.domain.OrderStatus;
import com.megharaj.orderinventory.order.infrastructure.OrderRepository;
import com.megharaj.orderinventory.payment.domain.PaymentAttempt;
import com.megharaj.orderinventory.payment.domain.PaymentSimulationOutcome;
import com.megharaj.orderinventory.payment.domain.PaymentStatus;
import com.megharaj.orderinventory.payment.infrastructure.PaymentAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InventoryReservationService reservationService;
    private final Clock clock;

    public PaymentTransactionService(
            OrderRepository orderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            InventoryReservationService reservationService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reservationService = reservationService;
        this.clock = clock;
    }

    @Transactional
    public PaymentPreparation prepare(
            UUID orderId,
            IdempotencyKey idempotencyKey,
            PaymentSimulationOutcome requestedOutcome) {
        CustomerOrder order = lockOrder(orderId);
        order.getItems().size();

        PaymentAttempt existing = paymentAttemptRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            validateRetry(existing, idempotencyKey, requestedOutcome);
            validateExistingState(order, existing);
            return toPreparation(existing, existing.getStatus() == PaymentStatus.PENDING);
        }

        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), "start payment");
        }
        paymentAttemptRepository.findByIdempotencyKey(idempotencyKey.value()).ifPresent(attempt -> {
            throw new ConflictException(
                    "Idempotency-Key has already been used for order " + attempt.getOrderId());
        });

        Instant now = clock.instant();
        order.beginPayment(now);
        PaymentAttempt attempt = PaymentAttempt.start(
                UUID.randomUUID(),
                orderId,
                idempotencyKey.value(),
                requestedOutcome,
                order.getTotalAmount(),
                order.getCurrency(),
                now);
        paymentAttemptRepository.save(attempt);
        return toPreparation(attempt, true);
    }

    @Transactional
    public OrderView complete(PaymentPreparation preparation, PaymentGatewayResult result) {
        CustomerOrder order = lockOrder(preparation.orderId());
        order.getItems().size();
        PaymentAttempt attempt = paymentAttemptRepository.findById(preparation.attemptId())
                .orElseThrow(() -> new InventoryConsistencyException(
                        "Payment attempt " + preparation.attemptId() + " was not found"));

        if (!attempt.getOrderId().equals(order.getId())) {
            throw new InventoryConsistencyException(
                    "Payment attempt " + attempt.getId() + " does not belong to order " + order.getId());
        }
        if (attempt.getStatus() != PaymentStatus.PENDING) {
            return OrderViewMapper.toView(order);
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new InventoryConsistencyException(
                    "Order " + order.getId() + " is " + order.getStatus()
                            + " while payment attempt " + attempt.getId() + " is pending");
        }

        Instant now = clock.instant();
        if (result.successful()) {
            reservationService.commitAll(order.getItems());
            attempt.succeed(result.providerReference(), now);
            order.confirm(now);
        } else {
            reservationService.releaseAll(order.getItems());
            attempt.fail(result.providerReference(), result.failureReason(), now);
            order.markPaymentFailed(now);
        }
        return OrderViewMapper.toView(order);
    }

    private CustomerOrder lockOrder(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order " + orderId + " was not found"));
    }

    private static void validateRetry(
            PaymentAttempt existing,
            IdempotencyKey idempotencyKey,
            PaymentSimulationOutcome requestedOutcome) {
        if (!existing.getIdempotencyKey().equals(idempotencyKey.value())) {
            throw new ConflictException(
                    "Order " + existing.getOrderId()
                            + " already has a payment attempt with a different Idempotency-Key");
        }
        if (existing.getRequestedOutcome() != requestedOutcome) {
            throw new ConflictException(
                    "A retried payment must use the same simulated outcome as the original request");
        }
    }

    private static void validateExistingState(CustomerOrder order, PaymentAttempt attempt) {
        if (attempt.getStatus() == PaymentStatus.PENDING
                && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new InventoryConsistencyException(
                    "Pending payment attempt " + attempt.getId()
                            + " has an inconsistent order status " + order.getStatus());
        }
        if (attempt.getStatus() == PaymentStatus.SUCCEEDED
                && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InventoryConsistencyException(
                    "Successful payment attempt " + attempt.getId()
                            + " has an inconsistent order status " + order.getStatus());
        }
        if (attempt.getStatus() == PaymentStatus.FAILED
                && order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new InventoryConsistencyException(
                    "Failed payment attempt " + attempt.getId()
                            + " has an inconsistent order status " + order.getStatus());
        }
    }

    private static PaymentPreparation toPreparation(PaymentAttempt attempt, boolean shouldCharge) {
        return new PaymentPreparation(
                attempt.getId(),
                attempt.getOrderId(),
                attempt.getAmount(),
                attempt.getCurrency(),
                new IdempotencyKey(attempt.getIdempotencyKey()),
                attempt.getRequestedOutcome(),
                shouldCharge);
    }
}
