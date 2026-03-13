-- Add COLLATERAL_LOCKED to loan_status
ALTER TYPE loan_status ADD VALUE 'COLLATERAL_LOCKED';

-- Create bitcoin_transaction_type enum
CREATE TYPE bitcoin_transaction_type AS ENUM ('DEPOSIT');

-- Create escrow_accounts table
CREATE TABLE escrow_accounts (
    loan_id UUID PRIMARY KEY,
    escrow_address VARCHAR(255) NOT NULL,
    current_balance_sats BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_escrow
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);

-- Create bitcoin_transactions table
CREATE TABLE bitcoin_transactions (
    tx_hash VARCHAR(255) PRIMARY KEY,
    loan_id UUID NOT NULL,
    type bitcoin_transaction_type NOT NULL,
    amount_sats BIGINT NOT NULL,
    confirmations INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_tx
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);
