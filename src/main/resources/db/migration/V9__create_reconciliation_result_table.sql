CREATE TABLE reconciliation_result
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    pg_transaction_id VARCHAR(100)   NOT NULL,
    order_id          VARCHAR(64)    NOT NULL,
    internal_amount   DECIMAL(15, 2) NOT NULL,
    pg_amount         DECIMAL(15, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        DATETIME       NOT NULL
);
