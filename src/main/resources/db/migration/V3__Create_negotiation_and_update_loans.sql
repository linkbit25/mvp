-- Add new status values to loan_status by recreating the check constraint
ALTER TABLE loans DROP CONSTRAINT IF EXISTS loan_status_check;
ALTER TABLE loans ADD CONSTRAINT loan_status_check CHECK (status IN ('NEGOTIATING', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED', 'AWAITING_SIGNATURES', 'AWAITING_FEE', 'CANCELLED'));

-- Add new columns to loans table
ALTER TABLE loans ADD COLUMN repayment_type VARCHAR(50) CHECK (repayment_type IN ('EMI', 'BULLET'));
ALTER TABLE loans ADD COLUMN emi_count INT;
ALTER TABLE loans ADD COLUMN emi_amount DECIMAL(19, 2);
ALTER TABLE loans ADD COLUMN total_repayment_amount DECIMAL(19, 2);
ALTER TABLE loans ADD COLUMN expected_ltv_percent INT;
ALTER TABLE loans ADD COLUMN margin_call_ltv_percent INT;
ALTER TABLE loans ADD COLUMN liquidation_ltv_percent INT;
ALTER TABLE loans ADD COLUMN agreement_hash VARCHAR(255);
ALTER TABLE loans ADD COLUMN borrower_signature TEXT;
ALTER TABLE loans ADD COLUMN lender_signature TEXT;

-- Create negotiation_messages table
CREATE TABLE negotiation_messages (
    id BIGSERIAL PRIMARY KEY,
    loan_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    message_text TEXT NOT NULL,
    is_system_message BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_message
        FOREIGN KEY(loan_id)
        REFERENCES loans(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sender_message
        FOREIGN KEY(sender_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);
