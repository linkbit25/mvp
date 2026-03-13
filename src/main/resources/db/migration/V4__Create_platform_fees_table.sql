-- Add AWAITING_COLLATERAL to loan_status
ALTER TABLE loans DROP CONSTRAINT IF EXISTS loan_status_check;
ALTER TABLE loans ADD CONSTRAINT loan_status_check CHECK (status IN ('NEGOTIATING', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED', 'AWAITING_SIGNATURES', 'AWAITING_FEE', 'CANCELLED', 'AWAITING_COLLATERAL'));

-- Create platform_fees table
CREATE TABLE platform_fees (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    amount_inr DECIMAL(19, 2) NOT NULL,
    payment_gateway_ref VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT platform_fee_status_check CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT fk_loan_fee
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);
