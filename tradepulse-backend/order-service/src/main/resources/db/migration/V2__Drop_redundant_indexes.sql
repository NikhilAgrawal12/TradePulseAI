-- Drop redundant indexes:
-- idx_order_user_id: covered by idx_order_user_id_created_at (user_id is leading column).
-- idx_order_items_order_id: covered by order_items_pkey on (order_id, stock_id) — order_id is leading column.
DROP INDEX IF EXISTS idx_order_user_id;
DROP INDEX IF EXISTS idx_order_items_order_id;

