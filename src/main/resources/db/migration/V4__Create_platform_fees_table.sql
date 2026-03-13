-- Add AWAITING_COLLATERAL to loan_status
ALTER TYPE loan_status ADD VALUE 'AWAITING_COLLATERAL';

-- Create platform_fee_status enum
CREATE TYPE platform_fee_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED');

-- Create platform_fees table
CREATE TABLE platform_fees (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    amount_inr DECIMAL(19, 2) NOT NULL,
    payment_gateway_ref VARCHAR(255),
    status platform_fee_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_fee
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE
);
