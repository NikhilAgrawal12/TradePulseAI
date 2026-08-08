-- Remove unused subtotal column; total remains the single persisted order amount.
ALTER TABLE orders DROP COLUMN IF EXISTS subtotal;

