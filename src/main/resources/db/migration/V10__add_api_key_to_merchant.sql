ALTER TABLE merchant ADD COLUMN api_key VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE merchant ADD CONSTRAINT uk_merchant_api_key UNIQUE (api_key);
UPDATE merchant SET api_key = 'test-api-key-1' WHERE id = 1;
UPDATE merchant SET api_key = 'test-api-key-2' WHERE id = 2;
ALTER TABLE merchant MODIFY COLUMN api_key VARCHAR(64) NOT NULL;
