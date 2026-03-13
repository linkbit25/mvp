-- Update loan_status enum to include new statuses
ALTER TABLE loans ALTER COLUMN status VARCHAR(255);
ALTER TABLE loans DROP CONSTRAINT IF EXISTS loan_status_check;
ALTER TABLE loans ADD CONSTRAINT loan_status_check CHECK (status IN ('NEGOTIATING', 'AWAITING_SIGNATURES', 'AWAITING_FEE', 'AWAITING_COLLATERAL', 'COLLATERAL_LOCKED', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED', 'CANCELLED', 'DISPUTE_OPEN', 'LIQUIDATED'));

-- Add fiat disbursement fields to loans table
ALTER TABLE loans ADD COLUMN fiat_disbursed_at TIMESTAMP;
ALTER TABLE loans ADD COLUMN fiat_received_confirmed_at TIMESTAMP;
ALTER TABLE loans ADD COLUMN disbursement_reference VARCHAR(255);
ALTER TABLE loans ADD COLUMN disbursement_proof_url VARCHAR(255);
