CREATE TABLE IF NOT EXISTS watchlist_items
(
    user_id    BIGINT         NOT NULL,
    stock_id   BIGINT         NOT NULL,
    created_at TIMESTAMP      NOT NULL,

    CONSTRAINT pk_watchlist_items
        PRIMARY KEY (user_id, stock_id)
);

        ALTER TABLE IF EXISTS watchlist_items DROP COLUMN IF EXISTS quantity;
ALTER TABLE IF EXISTS watchlist_items DROP COLUMN IF EXISTS updated_at;

ALTER TABLE IF EXISTS customer DROP CONSTRAINT IF EXISTS customer_pkey;
ALTER TABLE IF EXISTS customer DROP COLUMN IF EXISTS customer_id;
ALTER TABLE IF EXISTS customer DROP CONSTRAINT IF EXISTS customer_user_id_key;
ALTER TABLE IF EXISTS customer ADD CONSTRAINT customer_pkey PRIMARY KEY (user_id);

CREATE TABLE IF NOT EXISTS customer
(
    user_id BIGINT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(50) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    registration_date TIMESTAMP NOT NULL
);
