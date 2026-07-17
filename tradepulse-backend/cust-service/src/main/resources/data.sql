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

-- Create customer table with customer_id as PK and user_id as UNIQUE reference to auth-service
CREATE TABLE IF NOT EXISTS customer
(
    customer_id       BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL UNIQUE,
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

-- Migration for existing deployments: add customer_id PK, demote user_id to UNIQUE
CREATE SEQUENCE IF NOT EXISTS customer_customer_id_seq;

ALTER TABLE IF EXISTS customer
    ADD COLUMN IF NOT EXISTS customer_id BIGINT DEFAULT nextval('customer_customer_id_seq');

UPDATE customer SET customer_id = nextval('customer_customer_id_seq') WHERE customer_id IS NULL;

ALTER TABLE IF EXISTS customer DROP CONSTRAINT IF EXISTS customer_pkey;
ALTER TABLE IF EXISTS customer DROP CONSTRAINT IF EXISTS customer_user_id_key;

ALTER TABLE IF EXISTS customer ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE IF EXISTS customer ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE IF EXISTS customer ADD CONSTRAINT customer_pkey PRIMARY KEY (customer_id);
ALTER TABLE IF EXISTS customer ADD CONSTRAINT customer_user_id_key UNIQUE (user_id);
