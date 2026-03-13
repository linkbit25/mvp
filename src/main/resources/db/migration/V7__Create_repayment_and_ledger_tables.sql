-- Update loan_status enum to append REPAID
ALTER TABLE loans DROP CONSTRAINT IF EXISTS loan_status_check;
ALTER TABLE loans ADD CONSTRAINT loan_status_check CHECK (status IN ('NEGOTIATING', 'AWAITING_SIGNATURES', 'AWAITING_FEE', 'AWAITING_COLLATERAL', 'COLLATERAL_LOCKED', 'AGREED', 'ACTIVE', 'DEFAULTED', 'CLOSED', 'CANCELLED', 'DISPUTE_OPEN', 'LIQUIDATED', 'REPAID'));

-- Add outstanding balance fields to loans table
ALTER TABLE loans ADD COLUMN principal_outstanding DECIMAL;
ALTER TABLE loans ADD COLUMN interest_outstanding DECIMAL;
ALTER TABLE loans ADD COLUMN total_outstanding DECIMAL;

-- Create loan_emis table
CREATE TABLE loan_emis (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    emi_number INT NOT NULL,
    due_date DATE NOT NULL,
    emi_amount DECIMAL NOT NULL,
    amount_paid DECIMAL NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE')),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_loan_emis_loan FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);

-- Create loan_repayments table
CREATE TABLE loan_repayments (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    amount_inr DECIMAL NOT NULL,
    transaction_reference VARCHAR(255) NOT NULL,
    proof_url VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_loan_repayments_loan FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);

-- Create loan_ledger table
CREATE TABLE loan_ledger (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL,
    entry_type VARCHAR(50) NOT NULL CHECK (entry_type IN ('FIAT_DISBURSEMENT', 'BORROWER_REPAYMENT', 'INTEREST_ACCRUAL', 'COLLATERAL_RELEASE')),
    amount_inr DECIMAL NOT NULL,
    created_at TIMESTAMP NOT NULL,
    notes TEXT,
    CONSTRAINT fk_loan_ledger_loan FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE CASCADE
);
