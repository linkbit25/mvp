-- Add COLLATERAL_LOCKED to loan_status
ALTER TABLE loans DROP CONSTRAINT IF EXISTS loan_status_check;
ALTER TABLE loans ADD CONSTRAINT loan_status_check CHECK (status IN ('NEGOTIATING', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED', 'AWAITING_SIGNATURES', 'AWAITING_FEE', 'CANCELLED', 'AWAITING_COLLATERAL', 'COLLATERAL_LOCKED'));

-- Create escrow_accounts table
CREATE TABLE escrow_accounts (
    loan_id UUID PRIMARY KEY,
    escrow_address VARCHAR(255) NOT NULL,
    current_balance_sats BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_escrow
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);

-- Create bitcoin_transactions table
CREATE TABLE bitcoin_transactions (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    tx_hash VARCHAR(255) NOT NULL,
    loan_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount_sats BIGINT NOT NULL,
    confirmations INT NOT NULL DEFAULT 0,
    status VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bitcoin_transaction_type_check CHECK (type IN ('DEPOSIT')),
    CONSTRAINT fk_loan_tx
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);
