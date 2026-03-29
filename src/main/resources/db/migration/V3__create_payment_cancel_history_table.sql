CREATE TABLE payment_cancel_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    cancel_amount DECIMAL(15, 2) NOT NULL,
    cancel_reason VARCHAR(200),
    canceled_amount_before DECIMAL(15, 2) NOT NULL,
    canceled_amount_after DECIMAL(15, 2) NOT NULL,
    pg_cancel_transaction_id VARCHAR(100),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payment_id (payment_id),
    CONSTRAINT fk_cancel_history_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
