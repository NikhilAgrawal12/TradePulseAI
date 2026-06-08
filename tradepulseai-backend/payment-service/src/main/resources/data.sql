-- Keep this script non-empty when spring.sql.init.mode=always.
SELECT 1;

-- Convert payments.order_id from bigint to varchar so UUID order IDs from order-service are supported.
ALTER TABLE IF EXISTS payments
    ALTER COLUMN order_id TYPE VARCHAR(64)
    USING order_id::text;

