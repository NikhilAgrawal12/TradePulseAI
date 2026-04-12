CREATE TABLE IF NOT EXISTS watchlist_items
(
    user_id    BIGINT         NOT NULL,
    stock_id   BIGINT         NOT NULL,
    quantity   NUMERIC(18, 4) NOT NULL,
    created_at TIMESTAMP      NOT NULL,
    updated_at TIMESTAMP      NOT NULL,

    CONSTRAINT pk_watchlist_items
        PRIMARY KEY (user_id, stock_id)
);

CREATE TABLE IF NOT EXISTS customer
(
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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
    registration_date TIMESTAMP NOT NULL,
    user_id BIGINT UNIQUE NOT NULL
);
