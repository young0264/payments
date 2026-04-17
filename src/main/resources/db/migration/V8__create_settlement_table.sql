CREATE TABLE settlement
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    VARCHAR(64)    NOT NULL UNIQUE,
    merchant_id BIGINT         NOT NULL,
    pg_provider VARCHAR(50)    NOT NULL,
    amount      DECIMAL(15, 2) NOT NULL,
    fee_amount  DECIMAL(15, 2) NOT NULL,
    net_amount  DECIMAL(15, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME       NOT NULL,
    settled_at  DATETIME       NULL
);
