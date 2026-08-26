CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    requested_outcome VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL,
    provider_reference VARCHAR(128),
    failure_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_payment_attempt_order
        FOREIGN KEY (order_id) REFERENCES customer_orders (id),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_payment_requested_outcome CHECK (requested_outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_payment_attempts_status ON payment_attempts (status);
CREATE INDEX idx_payment_attempts_created_at ON payment_attempts (created_at);
