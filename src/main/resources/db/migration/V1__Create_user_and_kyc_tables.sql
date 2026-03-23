CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255),
    pseudonym VARCHAR(255),
    kyc_status VARCHAR(255) NOT NULL CHECK (kyc_status IN ('PENDING', 'SUBMITTED', 'VERIFIED', 'REJECTED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_kyc_details (
    user_id UUID PRIMARY KEY,
    full_legal_name VARCHAR(255),
    bank_account_number VARCHAR(255),
    ifsc VARCHAR(255),
    upi_id VARCHAR(255),
    national_id_hash VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_token
        FOREIGN KEY(user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
