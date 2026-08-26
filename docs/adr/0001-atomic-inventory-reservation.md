# ADR 0001: Use conditional database updates for inventory reservation

- Status: Accepted
- Date: 2026-08-26

## Context

A read followed by a write can oversell stock: concurrent transactions may both observe the
same available quantity before either decrements it. The service must also reserve every
item in a multi-item order or reserve none.

## Decision

Reserve each item with one conditional update:

```sql
UPDATE inventory
SET available_quantity = available_quantity - :quantity,
    reserved_quantity = reserved_quantity + :quantity
WHERE product_id = :productId
  AND available_quantity >= :quantity;
```

One affected row means success; zero means insufficient stock. All item updates execute in
one transaction and in sorted product-ID order.

Release and commit operations also include a `reserved_quantity >= :quantity` guard. A
failure of those guards is treated as an internal consistency error and rolls back the
transaction.

## Consequences

- Overselling is prevented by the database across all application instances.
- No application-level distributed lock is needed.
- Failed multi-item reservations roll back completely.
- Hot products serialize at one database row and may become throughput bottlenecks.
- The SQL is intentionally simple and supported by both PostgreSQL and H2 test mode.

## Alternatives considered

- **Application synchronization:** rejected because it does not work across instances.
- **Read then optimistic entity update:** valid, but requires retry handling under heavy
  contention and produces more round trips.
- **Pessimistic read lock:** valid, but the conditional update is shorter and avoids a
  separate availability read.
- **Redis counter as source of truth:** rejected for this scope because cross-store
  reconciliation would add failure modes; PostgreSQL remains authoritative.
