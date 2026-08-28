# Postman Testing Guide and API Payloads

The repository includes a ready-to-import Postman collection and local environment:

- `postman/Order-Inventory-Management.postman_collection.json`
- `postman/Local.postman_environment.json`

The collection contains every business endpoint, the configured Actuator health/observability
routes, automatic
variable capture, success/failure/cancellation workflows, idempotency checks, and expected error
examples.

## Import and run

1. Start the service:

   ```bash
   ./gradlew bootRun
   ```

2. In Postman, choose **Import** and select both JSON files under `postman/`.
3. Select the **Order Inventory - Local** environment.
4. Run the complete collection in numeric folder order.

Folder `01 - Prepare repeatable test data` creates fresh products and stores their generated IDs as
collection variables. This means the whole collection can be run repeatedly even when the H2 file
database already contains data. The collection also starts with the seeded Product A/B/C IDs, so
individual requests can be used without running setup first.

## Variables

| Variable | Purpose | Initial value |
|---|---|---|
| `baseUrl` | Service root | `http://localhost:8080` |
| `productAId` | Product A used by the assessment basket | `11111111-1111-1111-1111-111111111111` |
| `productBId` | Product B used by the assessment basket | `22222222-2222-2222-2222-222222222222` |
| `productCId` | Product C used by the assessment basket | `33333333-3333-3333-3333-333333333333` |
| `productDId` | Separate product for restock/failure/cancellation tests | Captured by setup |
| `successfulOrderId` | Order used by the successful payment flow | Captured after create order |
| `failedOrderId` | Order used by the payment-failure flow | Captured after create order |
| `cancelledOrderId` | Order used by the cancellation flow | Captured after create order |
| `successPaymentKey` | Idempotency key for successful payment and retry | Generated after create order |
| `failurePaymentKey` | Idempotency key for failed payment | Generated after create order |

## Common headers

JSON requests use:

```http
Content-Type: application/json
```

Payment additionally requires:

```http
Idempotency-Key: payment-customer-1001-001
```

The key must contain 8-128 characters, begin with an alphanumeric character, and contain only
letters, digits, `.`, `_`, `:`, or `-`. Retries for one order must reuse the same key and the same
`outcome` body.

An optional correlation header can be sent on any request:

```http
X-Correlation-ID: postman-run-001
```

The server returns the correlation ID in the response header and problem responses.

# Business API payloads

## 1. List products and inventory

```http
GET {{baseUrl}}/api/v1/products
```

Request body: none.

Example `200 OK` response:

```json
[
  {
    "productId": "11111111-1111-1111-1111-111111111111",
    "sku": "PRODUCT-A",
    "name": "Product A",
    "unitPrice": 19.99,
    "active": true,
    "availableQuantity": 10,
    "reservedQuantity": 0,
    "inventoryUpdatedAt": "2026-08-28T08:00:00Z"
  }
]
```

## 2. Get one product and inventory

```http
GET {{baseUrl}}/api/v1/products/{{productAId}}
```

Request body: none.

Example `200 OK` response:

```json
{
  "productId": "11111111-1111-1111-1111-111111111111",
  "sku": "PRODUCT-A",
  "name": "Product A",
  "unitPrice": 19.99,
  "active": true,
  "availableQuantity": 10,
  "reservedQuantity": 0,
  "inventoryUpdatedAt": "2026-08-28T08:00:00Z"
}
```

## 3. Create a product

```http
POST {{baseUrl}}/api/v1/products
Content-Type: application/json
```

Payload:

```json
{
  "sku": "PRODUCT-D",
  "name": "Product D",
  "unitPrice": 14.50,
  "initialQuantity": 25
}
```

Validation rules:

- `sku`: 2-64 letters, digits, underscores, or hyphens after normalization to uppercase.
- `name`: non-blank, at most 255 characters.
- `unitPrice`: non-negative, at most two decimal places, at most 17 integer digits.
- `initialQuantity`: zero or greater.

Example `201 Created` response:

```json
{
  "productId": "44444444-4444-4444-4444-444444444444",
  "sku": "PRODUCT-D",
  "name": "Product D",
  "unitPrice": 14.50,
  "active": true,
  "availableQuantity": 25,
  "reservedQuantity": 0,
  "inventoryUpdatedAt": "2026-08-28T08:05:00Z"
}
```

The response includes:

```http
Location: http://localhost:8080/api/v1/products/44444444-4444-4444-4444-444444444444
```

## 4. Restock a product

```http
POST {{baseUrl}}/api/v1/products/{{productDId}}/restocks
Content-Type: application/json
```

Payload:

```json
{
  "quantity": 10
}
```

`quantity` must be greater than zero. The endpoint atomically adds the quantity to
`availableQuantity` and returns the updated product view.

Example `200 OK` response:

```json
{
  "productId": "44444444-4444-4444-4444-444444444444",
  "sku": "PRODUCT-D",
  "name": "Product D",
  "unitPrice": 14.50,
  "active": true,
  "availableQuantity": 35,
  "reservedQuantity": 0,
  "inventoryUpdatedAt": "2026-08-28T08:06:00Z"
}
```

## 5. Create an order and reserve inventory

```http
POST {{baseUrl}}/api/v1/orders
Content-Type: application/json
```

Assessment payload:

```json
{
  "customerReference": "customer-1001",
  "items": [
    {
      "productId": "11111111-1111-1111-1111-111111111111",
      "quantity": 2
    },
    {
      "productId": "22222222-2222-2222-2222-222222222222",
      "quantity": 1
    },
    {
      "productId": "33333333-3333-3333-3333-333333333333",
      "quantity": 5
    }
  ]
}
```

Validation rules:

- `customerReference`: non-blank, at most 100 characters.
- `items`: 1-100 request lines and at most 100 distinct products after duplicate lines are merged.
- `productId`: valid UUID and existing active product.
- `quantity`: 1-1,000,000 per product after duplicates are merged.

The operation succeeds only when every item can be reserved. A failed line rolls back all earlier
reservations in the transaction.

Example `201 Created` response:

```json
{
  "orderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "customerReference": "customer-1001",
  "status": "INVENTORY_RESERVED",
  "totalAmount": 119.92,
  "currency": "USD",
  "items": [
    {
      "productId": "11111111-1111-1111-1111-111111111111",
      "sku": "PRODUCT-A",
      "name": "Product A",
      "quantity": 2,
      "unitPrice": 19.99,
      "lineTotal": 39.98
    },
    {
      "productId": "22222222-2222-2222-2222-222222222222",
      "sku": "PRODUCT-B",
      "name": "Product B",
      "quantity": 1,
      "unitPrice": 29.99,
      "lineTotal": 29.99
    },
    {
      "productId": "33333333-3333-3333-3333-333333333333",
      "sku": "PRODUCT-C",
      "name": "Product C",
      "quantity": 5,
      "unitPrice": 9.99,
      "lineTotal": 49.95
    }
  ],
  "createdAt": "2026-08-28T08:10:00Z",
  "updatedAt": "2026-08-28T08:10:00Z"
}
```

The response includes a `Location` header for `/api/v1/orders/{orderId}`.

## 6. Get an order

```http
GET {{baseUrl}}/api/v1/orders/{{successfulOrderId}}
```

Request body: none.

The response has the same shape as the create-order response. `status` reflects the latest state:
`INVENTORY_RESERVED`, `PAYMENT_PENDING`, `CONFIRMED`, `PAYMENT_FAILED`, or `CANCELLED`.

## 7. Simulate successful payment

```http
POST {{baseUrl}}/api/v1/orders/{{successfulOrderId}}/payments
Content-Type: application/json
Idempotency-Key: payment-customer-1001-001
```

Payload:

```json
{
  "outcome": "SUCCESS"
}
```

Example `200 OK` response excerpt:

```json
{
  "orderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "status": "CONFIRMED",
  "totalAmount": 119.92,
  "currency": "USD"
}
```

Successful payment consumes reserved inventory. Repeating the exact request with the same
`Idempotency-Key` returns the current confirmed order and does not consume inventory again.

A concurrent retry can briefly receive `PAYMENT_PENDING` while another request owns the payment
processing lease.

## 8. Simulate failed payment

```http
POST {{baseUrl}}/api/v1/orders/{{failedOrderId}}/payments
Content-Type: application/json
Idempotency-Key: payment-customer-1002-001
```

Payload:

```json
{
  "outcome": "FAILURE"
}
```

Example `200 OK` response excerpt:

```json
{
  "orderId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "status": "PAYMENT_FAILED"
}
```

Failed payment releases every reserved item back to available inventory. A retry must use the same
idempotency key and `FAILURE` outcome.

## 9. Cancel an order

```http
POST {{baseUrl}}/api/v1/orders/{{cancelledOrderId}}/cancellations
```

Request body: none.

Example `200 OK` response excerpt:

```json
{
  "orderId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
  "status": "CANCELLED"
}
```

Cancellation releases inventory for an `INVENTORY_RESERVED` order. Repeating the cancellation is
idempotent. A `PAYMENT_PENDING` or `CONFIRMED` order cannot be cancelled through this endpoint.

# Actuator APIs

These endpoints have no request body.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `{{baseUrl}}/actuator` | Discover exposed Actuator links |
| `GET` | `{{baseUrl}}/actuator/health` | Overall health |
| `GET` | `{{baseUrl}}/actuator/health/liveness` | Liveness probe |
| `GET` | `{{baseUrl}}/actuator/health/readiness` | Readiness probe |
| `GET` | `{{baseUrl}}/actuator/info` | Application metadata |
| `GET` | `{{baseUrl}}/actuator/metrics` | Available metric names |
| `GET` | `{{baseUrl}}/actuator/prometheus` | Prometheus text exposition |

Typical health response:

```json
{
  "status": "UP"
}
```

# Standard error response

Errors use Spring `ProblemDetail` with application-specific fields.

Example `409 Conflict` for insufficient inventory:

```json
{
  "type": "urn:problem:conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Insufficient inventory for product 44444444-4444-4444-4444-444444444444: requested 100, available 25",
  "instance": "/api/v1/orders",
  "errorCode": "conflict",
  "timestamp": "2026-08-28T08:20:00Z",
  "correlationId": "postman-run-001"
}
```

Common statuses:

| Status | Typical reason |
|---:|---|
| `400` | Validation failure, malformed UUID/JSON, missing header, unsupported idempotency key |
| `404` | Product or order not found |
| `409` | Insufficient inventory, duplicate SKU, invalid order state, conflicting retry data, concurrent update |
| `500` | Guarded inventory consistency failure or unexpected server failure |

# Collection execution sequence

The collection runner performs the following scenario automatically:

1. Verify health and observability endpoints.
2. Create fresh A/B/C products with inventory 10/5/20 and Product D with zero inventory.
3. List/read products and restock Product D to 25.
4. Create A x 2, B x 1, C x 5; pay successfully; retry the same payment idempotently.
5. Create a Product D order; fail payment; verify inventory was released.
6. Create another Product D order; cancel twice; verify inventory was released exactly once.
7. Exercise expected 400, 404, and 409 problem responses.
