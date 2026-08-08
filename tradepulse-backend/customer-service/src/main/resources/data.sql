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

-- Create customer table with user_id as PK (same identity as auth-service users.user_id)
CREATE TABLE IF NOT EXISTS customer
(
    user_id           BIGINT       NOT NULL PRIMARY KEY,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    phone_number      VARCHAR(50)  NOT NULL,
    address_line1     VARCHAR(255) NOT NULL,
    address_line2     VARCHAR(255),
    city              VARCHAR(100) NOT NULL,
    state             VARCHAR(100) NOT NULL,
    postal_code       VARCHAR(20)  NOT NULL,
    country           VARCHAR(100) NOT NULL,
    date_of_birth     DATE         NOT NULL,
    registration_date TIMESTAMP    NOT NULL
);

-- Drop redundant indexes: customer_pkey already indexes user_id; pk_watchlist_items leading column covers user_id lookups.
DROP INDEX IF EXISTS idx_customer_user_id;
DROP INDEX IF EXISTS idx_watchlist_items_user_id;

-- Migration: promote user_id to PK and remove legacy customer_id column
ALTER TABLE IF EXISTS customer DROP CONSTRAINT IF EXISTS customer_pkey;
ALTER TABLE IF EXISTS customer DROP CONSTRAINT IF EXISTS customer_user_id_key;
ALTER TABLE IF EXISTS customer ADD CONSTRAINT customer_pkey PRIMARY KEY (user_id);
ALTER TABLE IF EXISTS customer DROP COLUMN IF EXISTS customer_id;
DROP SEQUENCE IF EXISTS customer_customer_id_seq;
