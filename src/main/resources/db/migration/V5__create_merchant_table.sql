CREATE TABLE merchant
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL
);

INSERT INTO merchant (name, created_at) VALUES ('테스트 가맹점 A', NOW());
INSERT INTO merchant (name, created_at) VALUES ('테스트 가맹점 B', NOW());
