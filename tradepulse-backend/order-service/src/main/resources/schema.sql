-- Compatibility cleanup for long-lived volumes that still have legacy columns.
ALTER TABLE IF EXISTS orders DROP COLUMN IF EXISTS subtotal;

