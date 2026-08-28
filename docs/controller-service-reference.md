# Controller and Service Method Reference

This document complements the JavaDoc embedded in the source. It explains how each HTTP
controller method maps to an application-service method, where transactions begin and end,
and which business guarantees are enforced.

Generate browsable JavaDoc with:

```bash
./gradlew javadoc
```

The generated entry point is `build/docs/javadoc/index.html`.

## Layer responsibilities

- **Controllers** own HTTP routing, transport validation, headers, status codes, and resource
  locations. They do not implement business rules or persistence logic.
- **Application services** coordinate use cases and transaction boundaries.
- **Domain objects** enforce valid order and payment state transitions.
- **Repositories** provide persistence, row locking, and guarded inventory updates.
- **The payment gateway port** isolates the application from the simulated or future real
  payment-provider adapter.

## Controller methods

### `CatalogController`

Base path: `/api/v1/products`

| Method | HTTP endpoint | Delegates to | Result |
|---|---|---|---|
| `listProducts()` | `GET /api/v1/products` | `CatalogService.listCatalog()` | `200 OK` with products ordered by SKU and their current inventory |
| `getProduct(UUID)` | `GET /api/v1/products/{productId}` | `CatalogService.getCatalogItem(UUID)` | `200 OK`, or `404 Not Found` when the product is absent |
| `createProduct(CreateProductRequest)` | `POST /api/v1/products` | `CatalogService.createProduct(CreateProductCommand)` | `201 Created`, a `Location` header, and the new product/inventory view |
| `restock(UUID, RestockRequest)` | `POST /api/v1/products/{productId}/restocks` | `CatalogService.restock(UUID, long)` | `200 OK` with the updated available quantity |

Transport validation rejects blank or oversized names and SKUs, unsupported prices, negative
initial quantities, and non-positive restock quantities before or within the application service.

### `OrderController`

Base path: `/api/v1/orders`

| Method | HTTP endpoint | Delegates to | Result |
|---|---|---|---|
| `createOrder(String, CreateOrderRequest)` | `POST /api/v1/orders` | `OrderApplicationService.placeOrder(CreateOrderCommand, IdempotencyKey)` | `201 Created` with status `INVENTORY_RESERVED` only after all items are reserved; a repeated `Idempotency-Key` replays the original order |
| `getOrder(UUID)` | `GET /api/v1/orders/{orderId}` | `OrderApplicationService.getOrder(UUID)` | `200 OK` with immutable item snapshots and the current order status |
| `simulatePayment(UUID, String, SimulatePaymentRequest)` | `POST /api/v1/orders/{orderId}/payments` | `PaymentApplicationService.processPayment(...)` | `200 OK` with `PAYMENT_PENDING`, `CONFIRMED`, or `PAYMENT_FAILED` |
| `cancelOrder(UUID)` | `POST /api/v1/orders/{orderId}/cancellations` | `OrderApplicationService.cancelOrder(UUID)` | `200 OK` with `CANCELLED`, or the existing terminal failed/cancelled state |

The payment endpoint requires an `Idempotency-Key` header containing 8-128 supported characters.
Every retry for one order must reuse both the same key and the same simulated outcome.

## Application-service methods

### `CatalogService`

#### `createProduct(CreateProductCommand)`

Runs in one transaction. It normalizes the SKU, trims the name, validates monetary precision and
range, checks SKU uniqueness, creates the product, and creates its inventory row. A failure rolls
back both records.

Primary failures:

- `InvalidRequestException` for unsupported input.
- `ConflictException` for an existing normalized SKU.

#### `listCatalog()`

Runs in a read-only transaction. It returns immutable catalog views ordered by SKU and verifies
that every product has a corresponding inventory row.

#### `getCatalogItem(UUID)`

Runs in a read-only transaction. It loads a product and its inventory snapshot. A missing product
is reported as `ResourceNotFoundException` and mapped to HTTP 404.

#### `restock(UUID, long)`

Runs in one transaction. It validates a positive quantity and performs a database-side atomic
increment of available stock. The returned view is loaded after the increment.

### `OrderApplicationService`

#### `placeOrder(CreateOrderCommand, IdempotencyKey)`

Makes order creation idempotent. A prior order for the same `Idempotency-Key` is replayed
without side effects; otherwise it delegates to `createAndReserve` in its own transaction. If a
concurrent request wins the unique-key race, this request's reservation rolls back and the
winning order is returned.

#### `createAndReserve(CreateOrderCommand, IdempotencyKey)`

Runs in one transaction and provides all-or-nothing order creation:

1. Normalize and validate the customer reference.
2. Combine duplicate product lines.
3. Enforce per-line, distinct-item, and total-value limits.
4. Load products and reject missing or inactive products.
5. Snapshot SKU, name, price, and quantity into the order aggregate.
6. Atomically reserve every item in deterministic product-ID order.
7. Move the order to `INVENTORY_RESERVED` and persist it with its idempotency key.

If any reservation fails, the entire transaction rolls back, including reservations already made
for earlier lines.

#### `getOrder(UUID)`

Runs in a read-only transaction and loads the order together with its item snapshots.

#### `cancelOrder(UUID)`

Runs in one transaction and locks the order row to serialize cancellation against payment.

- `INVENTORY_RESERVED`: release all reservations, then move to `CANCELLED`.
- `CREATED`: move directly to `CANCELLED`.
- `CANCELLED` or `PAYMENT_FAILED`: return the existing state, making repeated calls idempotent.
- `PAYMENT_PENDING` or `CONFIRMED`: reject the transition.

### `InventoryReservationService`

Every method uses transaction propagation `MANDATORY`; callers must already own a transaction.
Items are sorted by product ID before mutation to reduce deadlock risk for multi-item concurrent
orders.

#### `reserveAll(List<OrderItem>)`

For each item, execute a guarded update that changes available and reserved quantities only when
sufficient stock exists. An affected-row count other than one raises
`InsufficientInventoryException`, causing the caller's transaction to roll back.

#### `releaseAll(List<OrderItem>)`

Moves reserved units back to available inventory after cancellation or failed payment. A guarded
update failure is treated as an inventory-consistency error rather than silently ignored.

#### `commitAll(List<OrderItem>)`

Consumes reserved units after successful payment. Available quantity is not changed here because
it was already reduced at reservation time.

### `PaymentApplicationService`

#### `processPayment(UUID, String, PaymentSimulationOutcome)`

Coordinates payment without holding a database transaction open during the gateway call:

1. Validate the idempotency key and requested outcome.
2. Ask `PaymentTransactionService.prepare(...)` to create or claim a persisted attempt.
3. Return the current order immediately when another request owns an active lease or the attempt
   is already complete.
4. Call `PaymentGateway.charge(...)` outside a database transaction when this request owns the
   lease.
5. Ask `PaymentTransactionService.complete(...)` to apply the result exactly once.

### `PaymentTransactionService`

#### `prepare(UUID, IdempotencyKey, PaymentSimulationOutcome)`

Runs in one short transaction and locks the order row. For a new payment it moves the order to
`PAYMENT_PENDING`, persists the attempt, and grants a processing token with an expiry. For a retry
it verifies the key and outcome, checks order/payment consistency, and grants ownership only when
no active lease exists or an old lease has expired.

#### `complete(PaymentPreparation, PaymentGatewayResult)`

Runs in a second short transaction and locks the order row. It verifies that the attempt still
belongs to the preparation and that the processing token still owns the lease.

- Success: commit every reservation, mark the attempt successful, and confirm the order.
- Failure: release every reservation, mark the attempt failed, and move the order to
  `PAYMENT_FAILED`.
- Stale or duplicate completion: return the current order without applying inventory changes
  again.

### `PaymentGateway`

`charge(PaymentCommand)` is the provider port. Real adapters must pass the application
idempotency key to the provider because a retry after lease expiry can overlap with a slow or
uncertain earlier provider call. The included `SimulatedPaymentGateway` derives a deterministic
provider reference from the order ID and idempotency key.

## Exception-to-HTTP mapping

| Condition | HTTP status | Problem code |
|---|---:|---|
| Missing product or order | `404` | `resource-not-found` |
| Invalid domain/application input | `400` | `invalid-request` |
| Bean-validation or malformed JSON/header/path value | `400` | `validation-failed` or `malformed-request` |
| Insufficient inventory, invalid order state, duplicate SKU, or idempotency conflict | `409` | `conflict` |
| Concurrent lock/update contention | `409` | `concurrent-update` |
| Guarded inventory consistency failure | `500` | `inventory-consistency-failure` |
| Unexpected failure | `500` | `internal-error` |

Every problem response includes a timestamp and, when available, the request correlation ID.
