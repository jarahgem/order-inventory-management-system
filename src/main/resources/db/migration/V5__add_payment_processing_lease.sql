ALTER TABLE payment_attempts
    ADD COLUMN processing_token UUID;

ALTER TABLE payment_attempts
    ADD COLUMN lease_expires_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE payment_attempts
    ADD CONSTRAINT chk_payment_processing_lease
    CHECK (
        (processing_token IS NULL AND lease_expires_at IS NULL)
        OR (
            status = 'PENDING'
            AND processing_token IS NOT NULL
            AND lease_expires_at IS NOT NULL
        )
    );

CREATE INDEX idx_payment_attempts_pending_lease
    ON payment_attempts (status, lease_expires_at);
