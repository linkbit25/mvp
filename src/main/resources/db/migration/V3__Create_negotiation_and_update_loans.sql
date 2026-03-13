-- Add new status values to loan_status
ALTER TYPE loan_status ADD VALUE 'AWAITING_SIGNATURES';
ALTER TYPE loan_status ADD VALUE 'AWAITING_FEE';
ALTER TYPE loan_status ADD VALUE 'CANCELLED';

-- Create repayment_type enum
CREATE TYPE repayment_type AS ENUM ('EMI', 'BULLET');

-- Add new columns to loans table
ALTER TABLE loans
ADD COLUMN repayment_type repayment_type,
ADD COLUMN emi_count INT,
ADD COLUMN emi_amount DECIMAL(19, 2),
ADD COLUMN total_repayment_amount DECIMAL(19, 2),
ADD COLUMN expected_ltv_percent INT,
ADD COLUMN margin_call_ltv_percent INT,
ADD COLUMN liquidation_ltv_percent INT,
ADD COLUMN agreement_hash VARCHAR(255),
ADD COLUMN borrower_signature TEXT,
ADD COLUMN lender_signature TEXT;

-- Create negotiation_messages table
CREATE TABLE negotiation_messages (
    id BIGSERIAL PRIMARY KEY,
    loan_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    message_text TEXT NOT NULL,
    is_system_message BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_message
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sender_message
        FOREIGN KEY(sender_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
