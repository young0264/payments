ALTER TABLE payment
    ADD COLUMN canceled_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00 AFTER amount;
