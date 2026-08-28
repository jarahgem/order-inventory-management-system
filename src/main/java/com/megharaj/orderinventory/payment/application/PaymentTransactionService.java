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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transactional stages around an external payment call.
 *
 * <p>The preparation stage locks the order, persists or reclaims a payment attempt, and grants a
 * time-limited processing lease. The completion stage verifies lease ownership before applying
 * the payment result and its inventory compensation exactly once.
 */
@Service
public class PaymentTransactionService {

    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final InventoryReservationService reservationService;
    private final Clock clock;
    private final Duration processingLease;

    /**
     * Creates the transactional payment service.
     *
     * @param orderRepository order persistence and locking gateway
     * @param paymentAttemptRepository payment-attempt persistence gateway
     * @param reservationService inventory commit and release service
     * @param clock source of lease and transition timestamps
     * @param processingLease duration for which one request owns a pending gateway call
     * @throws IllegalArgumentException when the configured lease is null or not positive
     */
    public PaymentTransactionService(
            OrderRepository orderRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            InventoryReservationService reservationService,
            Clock clock,
            @Value("${payment.processing-lease:30s}") Duration processingLease) {
        this.orderRepository = orderRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reservationService = reservationService;
        this.clock = clock;
        if (processingLease == null || processingLease.isZero() || processingLease.isNegative()) {
            throw new IllegalArgumentException("payment.processing-lease must be greater than zero");
        }
        this.processingLease = processingLease;
    }

    /**
     * Starts a payment attempt or evaluates an idempotent retry under an order-row lock.
     *
     * <p>A new attempt moves the order to {@code PAYMENT_PENDING}. An existing pending attempt can
     * be reclaimed only after its processing lease expires.
     *
     * @param orderId order being paid
     * @param idempotencyKey validated idempotency key
     * @param requestedOutcome simulator result that must remain stable across retries
     * @return payment preparation indicating whether this request may call the gateway
     * @throws ResourceNotFoundException when the order does not exist
     * @throws ConflictException when retry data conflicts with the persisted attempt
     * @throws InvalidOrderStateException when a new payment cannot start from the current state
     * @throws InventoryConsistencyException when order and payment states disagree
     */
    @Transactional
    public PaymentPreparation prepare(
            UUID orderId,
            IdempotencyKey idempotencyKey,
            PaymentSimulationOutcome requestedOutcome) {
        CustomerOrder order = lockOrder(orderId);
        order.getItems().size();
        Instant now = clock.instant();

        PaymentAttempt existing = paymentAttemptRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            validateRetry(existing, idempotencyKey, requestedOutcome);
            validateExistingState(order, existing);

            boolean claimed = false;
            if (existing.getStatus() == PaymentStatus.PENDING) {
                claimed = existing.tryClaim(
                        UUID.randomUUID(), now, now.plus(processingLease));
            }
            return toPreparation(existing, claimed);
        }

        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            throw new InvalidOrderStateException(orderId, order.getStatus(), "start payment");
        }
        paymentAttemptRepository.findByIdempotencyKey(idempotencyKey.value()).ifPresent(attempt -> {
            throw new ConflictException(
                    "Idempotency-Key has already been used for order " + attempt.getOrderId());
        });

        UUID processingToken = UUID.randomUUID();
        order.beginPayment(now);
        PaymentAttempt attempt = PaymentAttempt.start(
                UUID.randomUUID(),
                orderId,
                idempotencyKey.value(),
                requestedOutcome,
                order.getTotalAmount(),
                order.getCurrency(),
                processingToken,
                now.plus(processingLease),
                now);
        paymentAttemptRepository.save(attempt);
        return toPreparation(attempt, true);
    }

    /**
     * Applies a gateway result when the preparation still owns the active processing lease.
     *
     * <p>Success consumes reserved inventory and confirms the order. Failure releases inventory
     * and marks the order payment-failed. A stale or already-completed preparation returns the
     * current state without applying the result again.
     *
     * @param preparation persisted attempt and lease-ownership context
     * @param result gateway outcome and provider reference
     * @return current order view after applying or ignoring the result
     * @throws ResourceNotFoundException when the order no longer exists
     * @throws InventoryConsistencyException when persisted payment and order data disagree
     */
    @Transactional
    public OrderView complete(PaymentPreparation preparation, PaymentGatewayResult result) {
        CustomerOrder order = lockOrder(preparation.orderId());
        order.getItems().size();
        PaymentAttempt attempt = paymentAttemptRepository.findById(preparation.attemptId())
                .orElseThrow(() -> new InventoryConsistencyException(
                        "Payment attempt " + preparation.attemptId() + " was not found"));

        validatePreparation(preparation, order, attempt);
        if (attempt.getStatus() != PaymentStatus.PENDING) {
            return OrderViewMapper.toView(order);
        }
        if (!attempt.isOwnedBy(preparation.processingToken())) {
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

    private static void validatePreparation(
            PaymentPreparation preparation,
            CustomerOrder order,
            PaymentAttempt attempt) {
        if (!attempt.getOrderId().equals(order.getId())) {
            throw new InventoryConsistencyException(
                    "Payment attempt " + attempt.getId() + " does not belong to order " + order.getId());
        }
        if (!attempt.getIdempotencyKey().equals(preparation.idempotencyKey().value())) {
            throw new InventoryConsistencyException(
                    "Payment preparation does not match attempt " + attempt.getId());
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
                shouldCharge ? attempt.getProcessingToken() : null,
                shouldCharge);
    }
}
