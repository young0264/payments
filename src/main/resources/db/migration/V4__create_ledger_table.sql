CREATE TABLE ledger
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id       BIGINT         NOT NULL,
    event            VARCHAR(30)    NOT NULL,
    amount           DECIMAL(15, 2) NOT NULL,
    signed_amount    DECIMAL(15, 2) NOT NULL,
    group_id         VARCHAR(36)    NOT NULL,
    is_cancellation  BOOLEAN        NOT NULL DEFAULT FALSE,
    parent_ledger_id BIGINT         NULL,
    memo             VARCHAR(500)   NULL,
    created_at       DATETIME       NOT NULL,
    INDEX idx_ledger_payment_id (payment_id),
    CONSTRAINT fk_ledger_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
);
