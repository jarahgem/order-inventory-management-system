-- Makes order creation idempotent: a client-supplied Idempotency-Key uniquely identifies an
-- order so that safe retries return the original order instead of creating a duplicate.
ALTER TABLE customer_orders
    ADD COLUMN idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX uq_customer_orders_idempotency_key
    ON customer_orders (idempotency_key);
