-- Remove tax column from orders table (stocks are not taxed in USA)
ALTER TABLE orders DROP COLUMN IF EXISTS tax;

