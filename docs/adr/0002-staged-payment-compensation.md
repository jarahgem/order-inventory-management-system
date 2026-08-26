# ADR 0002: Stage payment and use compensating inventory actions

- Status: Accepted
- Date: 2026-08-26

## Context

A payment provider is external and cannot participate in the local database transaction.
Holding a database transaction open during the network call would waste connections, keep
locks for an unbounded period, and still would not provide atomicity across systems.

## Decision

Use a small saga-like workflow:

1. In a short transaction, lock the order, create a unique pending payment attempt, and
   change the order to `PAYMENT_PENDING`.
2. Commit.
3. Call the `PaymentGateway` port with an idempotency key.
4. In a second transaction, lock the order and either:
   - commit reserved inventory and confirm the order, or
   - release reserved inventory and mark payment failed.

A retry must use the same idempotency key and simulated outcome. Completed attempts return
the existing result.

## Consequences

- No remote call occurs inside a database transaction.
- Payment retries cannot consume or release inventory twice.
- A process crash can leave a pending attempt, but it is durable and retryable.
- Production operation needs a reconciliation worker and provider-side idempotency.
- A failed payment is terminal in this exercise; adding retries would require an explicit
  state and reservation-expiry policy.

## Alternatives considered

- **Single long transaction:** rejected because it cannot make an external payment atomic
  and harms database availability.
- **Release inventory before payment:** rejected because successful payment could then have
  no stock.
- **Distributed transaction/2PC:** rejected because typical payment providers do not
  support it and operational complexity is disproportionate.
- **Fully asynchronous broker workflow:** a good future evolution, but unnecessary for a
  runnable assessment; the persisted states already support that migration.
