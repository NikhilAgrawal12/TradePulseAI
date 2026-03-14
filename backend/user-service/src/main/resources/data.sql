-- Create table for AppUser entity
CREATE TABLE IF NOT EXISTS app_user
(
    user_id           UUID PRIMARY KEY,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    email             VARCHAR(255) UNIQUE NOT NULL,
    phone_number      VARCHAR(20) NOT NULL,
    address           VARCHAR(255) NOT NULL,
    city              VARCHAR(100) NOT NULL,
    state             VARCHAR(100) NOT NULL,
    country           VARCHAR(100) NOT NULL,
    date_of_birth     DATE NOT NULL,
    registration_date DATE NOT NULL
    );

-- Insert all users only if they do not exist
INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '123e4567-e89b-12d3-a456-426614174000', 'John', 'Doe', 'john.doe@example.com', '+16173787569',
       '123 Main St', 'Springfield', 'MA', 'USA', '1985-06-15', '2024-01-10'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '123e4567-e89b-12d3-a456-426614174000');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '123e4567-e89b-12d3-a456-426614174001', 'Jane', 'Smith', 'jane.smith@example.com', '+16175551234',
       '456 Elm St', 'Shelbyville', 'MA', 'USA', '1990-09-23', '2023-12-01'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '123e4567-e89b-12d3-a456-426614174001');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '123e4567-e89b-12d3-a456-426614174002', 'Alice', 'Johnson', 'alice.johnson@example.com', '+16176667890',
       '789 Oak St', 'Capital City', 'MA', 'USA', '1978-03-12', '2022-06-20'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '123e4567-e89b-12d3-a456-426614174002');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '123e4567-e89b-12d3-a456-426614174003', 'Bob', 'Brown', 'bob.brown@example.com', '+16178889900',
       '321 Pine St', 'Springfield', 'MA', 'USA', '1982-11-30', '2023-05-14'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '123e4567-e89b-12d3-a456-426614174003');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '123e4567-e89b-12d3-a456-426614174004', 'Emily', 'Davis', 'emily.davis@example.com', '+16179990011',
       '654 Maple St', 'Shelbyville', 'MA', 'USA', '1995-02-05', '2024-03-01'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '123e4567-e89b-12d3-a456-426614174004');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174005', 'Michael', 'Green', 'michael.green@example.com', '+16170001122',
       '987 Cedar St', 'Springfield', 'MA', 'USA', '1988-07-25', '2024-02-15'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174005');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174006', 'Sarah', 'Taylor', 'sarah.taylor@example.com', '+16171112233',
       '123 Birch St', 'Shelbyville', 'MA', 'USA', '1992-04-18', '2023-08-25'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174006');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174007', 'David', 'Wilson', 'david.wilson@example.com', '+16172223344',
       '456 Ash St', 'Capital City', 'MA', 'USA', '1975-01-11', '2022-10-10'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174007');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174008', 'Laura', 'White', 'laura.white@example.com', '+16173334455',
       '789 Palm St', 'Springfield', 'MA', 'USA', '1989-09-02', '2024-04-20'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174008');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174009', 'James', 'Harris', 'james.harris@example.com', '+16174445566',
       '321 Cherry St', 'Shelbyville', 'MA', 'USA', '1993-11-15', '2023-06-30'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174009');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174010', 'Emma', 'Moore', 'emma.moore@example.com', '+16175556677',
       '654 Spruce St', 'Capital City', 'MA', 'USA', '1980-08-09', '2023-01-22'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174010');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174011', 'Ethan', 'Martinez', 'ethan.martinez@example.com', '+16176667788',
       '987 Redwood St', 'Springfield', 'MA', 'USA', '1984-05-03', '2024-05-12'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174011');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174012', 'Sophia', 'Clark', 'sophia.clark@example.com', '+16177778899',
       '123 Hickory St', 'Shelbyville', 'MA', 'USA', '1991-12-25', '2022-11-11'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174012');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174013', 'Daniel', 'Lewis', 'daniel.lewis@example.com', '+16178889900',
       '456 Cypress St', 'Capital City', 'MA', 'USA', '1976-06-08', '2023-09-19'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174013');

INSERT INTO app_user (user_id, first_name, last_name, email, phone_number, address, city, state, country, date_of_birth, registration_date)
SELECT '223e4567-e89b-12d3-a456-426614174014', 'Isabella', 'Walker', 'isabella.walker@example.com', '+16179990011',
       '789 Willow St', 'Springfield', 'MA', 'USA', '1987-10-17', '2024-03-29'
    WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE user_id = '223e4567-e89b-12d3-a456-426614174014');