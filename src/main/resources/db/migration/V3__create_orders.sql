CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL CHECK (total_amount >= 0),
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_customer_order_status CHECK (
        status IN (
            'CREATED',
            'INVENTORY_RESERVED',
            'PAYMENT_PENDING',
            'CONFIRMED',
            'PAYMENT_FAILED',
            'CANCELLED'
        )
    )
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT fk_order_item_order
        FOREIGN KEY (order_id) REFERENCES customer_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_order_item_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_customer_orders_status ON customer_orders (status);
CREATE INDEX idx_customer_orders_created_at ON customer_orders (created_at);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
