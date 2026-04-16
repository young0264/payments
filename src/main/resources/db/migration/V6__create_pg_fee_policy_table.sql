CREATE TABLE pg_fee_policy
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    pg_provider VARCHAR(50)   NOT NULL UNIQUE,
    fee_rate    DECIMAL(5, 4) NOT NULL,
    created_at  DATETIME      NOT NULL
);

INSERT INTO pg_fee_policy (pg_provider, fee_rate, created_at) VALUES ('MOCK_PG_A', 0.0300, NOW());
INSERT INTO pg_fee_policy (pg_provider, fee_rate, created_at) VALUES ('MOCK_PG_B', 0.0200, NOW());
