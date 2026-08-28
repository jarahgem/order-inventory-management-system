# ADR 0004: Use a database-backed payment processing lease

- Status: Accepted
- Date: 2026-08-28

## Context

The payment endpoint is idempotent and the gateway call deliberately runs outside the
local database transaction. Without additional ownership, two concurrent retries can both
observe the same pending attempt and call the external provider at the same time. A
provider-side idempotency key protects against duplicate charges, but duplicate network
calls still waste capacity and make failure handling harder to reason about.

Holding an order-row lock during the external call would prevent duplicate calls, but it
would also keep a database transaction and connection open for an unbounded network
operation. A process can also stop after it has claimed work, so permanent ownership is not
safe.

## Decision

Store a `processing_token` and `lease_expires_at` on each pending payment attempt.

1. The request that creates the attempt receives a new token and lease.
2. A retry that arrives while the lease is active returns the current order state and does
   not call the gateway.
3. A retry that arrives after lease expiry can atomically claim a new token while holding
   the order-row lock.
4. Completion is accepted only from the token that currently owns the attempt.
5. Success or failure clears the lease as part of the same transaction that updates the
   order and inventory.

Every gateway invocation still uses the original idempotency key. This is required because
a slow original owner and a takeover can overlap around lease expiry.

## Consequences

- Concurrent retries produce at most one active gateway call during a lease window.
- No database transaction is held during the gateway call.
- A crashed request can be recovered by a later retry after lease expiry.
- A stale gateway response cannot complete an attempt after another request has taken over.
- The lease duration is operational configuration and should exceed normal provider
  latency while remaining short enough for acceptable recovery.
- A scheduled reconciliation worker is still recommended so recovery does not depend only
  on client retries.

## Alternatives considered

- **Rely only on provider idempotency:** safe for charges at a capable provider, but permits
  duplicate calls and does not control local ownership.
- **Keep the database lock during the gateway call:** rejected because it consumes a
  connection and holds locks across an unbounded remote operation.
- **In-memory single-flight map:** rejected because it is not correct across multiple
  application instances and loses state on restart.
- **Permanent claimed flag:** rejected because a crashed owner would leave the attempt
  unrecoverable without manual intervention.
